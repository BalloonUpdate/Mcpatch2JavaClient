package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.exceptions.CloudConfigException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 云端配置加解密工具类。
 * <p>
 * 提供 AES-256-GCM 解密、AES 密钥指纹校验、RSA-PSS 签名验证、HMAC-SHA256 请求签名等功能。
 * 仅使用 JDK 标准库（javax.crypto、java.security），无第三方加密库依赖。
 */
public class CloudCrypto {

    /**
     * GCM 认证标签长度（位），128 位 = 16 字节
     */
    public static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * GCM Nonce 长度（字节），12 字节 = 96 位
     */
    public static final int GCM_NONCE_LENGTH = 12;

    /**
     * AES-256 密钥长度（字节），32 字节 = 256 位
     */
    public static final int AES256_KEY_LENGTH = 32;

    /**
     * AES-256-GCM 解密。
     * <p>
     * 将服务端返回的加密数据解密为明文 YAML 字符串。
     * 使用 JDK 标准库的 javax.crypto.Cipher 类，采用 AES/GCM/NoPadding 算法。
     * GCM 认证标签从单独的响应头中获取，解密时与密文拼接后由 Cipher 自动验证。
     *
     * @param ciphertext 密文字节数组（不含认证标签）
     * @param authTag    GCM 认证标签字节数组（16 字节）
     * @param keyHex     64 位十六进制字符串（32 字节）的 AES-256 密钥
     * @param nonceHex   24 位十六进制字符串（12 字节）的 GCM Nonce
     * @return UTF-8 编码的明文字符串
     * @throws Exception 解密失败或认证标签验证失败时抛出
     */
    public static String decryptAES256GCM(byte[] ciphertext, byte[] authTag, String keyHex, String nonceHex)
            throws Exception {
        byte[] key = hexToBytes(keyHex);
        byte[] nonce = hexToBytes(nonceHex);

        try {
            if (key.length != AES256_KEY_LENGTH) {
                throw new IllegalArgumentException("AES-256 密钥必须为32字节，当前: " + key.length);
            }
            if (nonce.length != GCM_NONCE_LENGTH) {
                throw new IllegalArgumentException("GCM Nonce必须为12字节，当前: " + nonce.length);
            }
            if (authTag == null || authTag.length != GCM_TAG_LENGTH_BITS / 8) {
                throw new IllegalArgumentException("GCM 认证标签必须为16字节");
            }

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Java GCM 实现：需要将 ciphertext + authTag 拼接后传入 doFinal
            // Cipher 会自动提取最后 16 字节作为认证标签并验证
            byte[] combined = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(authTag, 0, combined, ciphertext.length, authTag.length);

            byte[] decrypted = cipher.doFinal(combined);
            String result = new String(decrypted, StandardCharsets.UTF_8);

            // 清零临时缓冲区
            Arrays.fill(combined, (byte) 0);

            return result;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * AES 密钥指纹校验。
     * <p>
     * 服务端响应头中的 X-AES-Fingerprint 字段包含 AES 密钥的 SHA-256 指纹。
     * 客户端应在解密前先校验指纹是否匹配，以确保密钥一致性。
     * 如果指纹不匹配，说明服务端使用的 AES 密钥已更换，必须立即回退并告知管理员。
     *
     * @param keyHex            本地 AES 密钥的十六进制字符串
     * @param serverFingerprint 服务端返回的密钥指纹（AA:BB:CC:... 格式或纯十六进制）
     * @return 指纹是否匹配
     */
    public static boolean verifyAesFingerprint(String keyHex, String serverFingerprint) {
        byte[] keyBytes = hexToBytes(keyHex);
        try {
            String localFp = sha256Fingerprint(keyBytes);

            // 将服务端指纹中的冒号去除，统一为纯十六进制格式后比较（忽略大小写）
            String normalizedServerFp = serverFingerprint.replace(":", "");
            return localFp.equalsIgnoreCase(normalizedServerFp);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * RSA-PSS 签名验证。
     * <p>
     * 用于验证服务端响应的 X-Config-Signature 头，确保配置内容未被篡改。
     * 使用 RSASSA-PSS 填充方案（SHA-256 + MGF1-SHA-256），比 PKCS#1 v1.5 更安全。
     * 公钥以 PEM 格式提供（-----BEGIN PUBLIC KEY----- ... -----END PUBLIC KEY-----）。
     *
     * @param data            待验证的原始数据（解密后的明文 YAML）
     * @param signatureBase64 Base64 编码的 RSA-PSS 签名
     * @param publicKeyPem    PEM 格式的 RSA 公钥
     * @return 签名是否有效
     */
    public static boolean verifyRSASignature(String data, String signatureBase64, String publicKeyPem) {
        try {
            // 从 PEM 格式提取公钥字节
            String publicKeyContent = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // RSASSA-PSS 签名验证
            Signature sig = Signature.getInstance("RSASSA-PSS");

            // 配置 PSS 参数：SHA-256 + MGF1-SHA-256 + 32字节盐
            PSSParameterSpec pssSpec = new PSSParameterSpec(
                    "SHA-256",           // 摘要算法
                    "MGF1",              // 掩码生成函数
                    MGF1ParameterSpec.SHA256,  // MGF1 摘要算法
                    32,                  // 盐长度（与哈希长度一致）
                    1                    // trailerField
            );
            sig.setParameter(pssSpec);
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HMAC-SHA256 请求签名。
     * <p>
     * 用于对客户端发送到云端配置 API 的请求进行签名，防止请求被伪造或重放。
     * <p>
     * 签名算法（与云端服务端一致）：
     * <pre>
     * 签名原文 = timestamp + apiKey + requestPath  （拼接，无分隔符）
     * 签名算法 = HMAC-SHA256(签名原文, hmacSecret)
     * 签名结果 = 64位十六进制小写字符串
     * </pre>
     *
     * @param hmacSecret  HMAC 密钥（64 位十六进制字符串，32 字节）
     * @param timestamp   秒级时间戳字符串
     * @param apiKey      API 访问密钥（Bearer Token）
     * @param requestPath 请求路径（如 /api/client）
     * @return 小写十六进制格式的 HMAC-SHA256 签名字符串（64 位）
     * @throws Exception HMAC 计算失败时抛出
     */
    public static String signHmacSHA256(String hmacSecret, String timestamp, String apiKey, String requestPath) throws Exception {
        // 签名原文 = timestamp + apiKey + requestPath（拼接，无分隔符）
        String payload = timestamp + apiKey + requestPath;

        // hmacSecret 是 64 位 hex 字符串，需要 hexToBytes 解码为 32 字节原始密钥
        byte[] keyBytes = hexToBytes(hmacSecret);
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "HmacSHA256");

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * 密钥碎片 XOR 还原（Layer 3 防逆向）。
     * <p>
     * 将 3 个碎片通过 XOR 运算还原为原始密钥，防止通过逆向 JAR 直接获取完整密钥。
     * <p>
     * 还原算法：{@code original = frag1 XOR frag2 XOR frag3}
     *
     * @param frag1Hex 碎片1（十六进制字符串）
     * @param frag2Hex 碎片2（十六进制字符串）
     * @param frag3Hex 碎片3（十六进制字符串）
     * @return 还原后的原始密钥（十六进制字符串）
     * @throws IllegalArgumentException 碎片长度不一致或为空时抛出
     */
    public static String xorReconstruct(String frag1Hex, String frag2Hex, String frag3Hex) {
        byte[] frag1 = hexToBytes(frag1Hex);
        byte[] frag2 = hexToBytes(frag2Hex);
        byte[] frag3 = hexToBytes(frag3Hex);

        if (frag1.length == 0 || frag2.length == 0 || frag3.length == 0) {
            throw new IllegalArgumentException("密钥碎片不能为空");
        }
        if (frag1.length != frag2.length || frag2.length != frag3.length) {
            throw new IllegalArgumentException(
                    "密钥碎片长度不一致: frag1=" + frag1.length + ", frag2=" + frag2.length + ", frag3=" + frag3.length);
        }

        byte[] original = new byte[frag1.length];
        for (int i = 0; i < frag1.length; i++) {
            original[i] = (byte) (frag1[i] ^ frag2[i] ^ frag3[i]);
        }

        return bytesToHex(original);
    }

    /**
     * 将十六进制字符串转换为字节数组。
     *
     * @param hex 十六进制字符串（长度必须为偶数）
     * @return 字节数组
     */
    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }

        // 去除可能的冒号分隔符
        hex = hex.replace(":", "");

        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数: " + len);
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 将字节数组转换为十六进制字符串（小写）。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 计算字节数组的 SHA-256 指纹。
     * <p>
     * 返回纯十六进制格式的指纹字符串（无冒号分隔，小写）。
     *
     * @param data 输入字节数组
     * @return SHA-256 指纹（64 位十六进制字符串）
     */
    static String sha256Fingerprint(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    private CloudCrypto() {
        // 禁止实例化
    }
}
