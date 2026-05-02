package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.logging.Log;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 安全加解密助手（核心安全类）。
 * <p>
 * 负责在内存中安全地还原 AES/HMAC 密钥、执行加解密和签名操作，
 * 并在操作完成后立即清零所有敏感数据。
 * <p>
 * 当前加密方案：AES-256-GCM（服务端已从 CBC 升级为 GCM）。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>完整的密钥只作为局部变量存在，方法结束时通过 finally 块确保清零</li>
 *   <li>密钥不会泄漏到堆内存中长期存在（不存在于类的字段中）</li>
 *   <li>所有密钥碎片在方法结束后也被清零</li>
 * </ul>
 */
public class SecureAesHelper {

    /**
     * GCM 认证标签长度（位）
     */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * 安全解密配置数据（AES-256-GCM）。
     * <p>
     * 密钥还原流程：
     * <ol>
     *   <li>从 BuildInfo.BUILD_SIGNATURE 获取 Fragment1（hex → bytes）</li>
     *   <li>从 ThemeConfig.getThemeSeed() 获取 Fragment2（bytes）</li>
     *   <li>从 FragmentStore.loadFragment3() 获取 Fragment3（bytes）</li>
     *   <li>XOR 还原：aesKey = frag1 XOR frag2 XOR frag3</li>
     * </ol>
     * GCM 认证标签从响应头单独获取，与密文拼接后由 Cipher 自动验证。
     * 解密完成后，所有密钥和碎片数据立即用 Arrays.fill 清零。
     *
     * @param encryptedBase64 Base64 编码的 AES-256-GCM 密文字符串（不含认证标签）
     * @param nonceHex        24 位十六进制字符串（12 字节）的 GCM Nonce
     * @param authTagHex      十六进制编码的 GCM 认证标签（32 字符 = 16 字节）
     * @return UTF-8 编码的明文字符串
     * @throws Exception 解密失败、认证标签验证失败或碎片缺失时抛出
     */
    public static String decryptConfig(String encryptedBase64, String nonceHex, String authTagHex) throws Exception {
        // Step 1: 收集三个碎片
        byte[] frag1 = hexToBytes(BuildInfo.BUILD_SIGNATURE);
        byte[] frag2 = ThemeConfig.getThemeSeed();
        byte[] frag3 = FragmentStore.loadFragment3();

        if (frag3 == null) {
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            throw new SecurityException("密钥碎片 Fragment3 缺失，无法解密（首次启动时应自动初始化）");
        }

        try {
            // Step 2: 内存中还原完整密钥
            byte[] aesKey = KeySharding.assembleKey(frag1, frag2, frag3);

            try {
                // Step 3: AES-256-GCM 解密
                byte[] ciphertext = Base64.getDecoder().decode(encryptedBase64);
                byte[] authTag = hexToBytes(authTagHex);

                return decryptGCM(ciphertext, authTag, aesKey, hexToBytes(nonceHex));
            } finally {
                // Step 4: 立即清零 AES 密钥内存
                Arrays.fill(aesKey, (byte) 0);
            }
        } finally {
            // 清零所有碎片内存
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            Arrays.fill(frag3, (byte) 0);
        }
    }

    /**
     * 安全解密配置数据（直接传入密文和认证标签字节数组版本，用于本地缓存解密）。
     * <p>
     * 与 decryptConfig(String, String, String) 相同的安全机制，
     * 但接受原始字节数组作为输入，用于从二进制缓存文件中读取的密文和认证标签。
     *
     * @param ciphertext AES-256-GCM 密文字节数组（不含认证标签）
     * @param authTag    GCM 认证标签字节数组（16 字节）
     * @param nonceHex   24 位十六进制字符串（12 字节）的 GCM Nonce
     * @return UTF-8 编码的明文字符串
     * @throws Exception 解密失败或碎片缺失时抛出
     */
    public static String decryptConfigBytes(byte[] ciphertext, byte[] authTag, String nonceHex) throws Exception {
        byte[] frag1 = hexToBytes(BuildInfo.BUILD_SIGNATURE);
        byte[] frag2 = ThemeConfig.getThemeSeed();
        byte[] frag3 = FragmentStore.loadFragment3();

        if (frag3 == null) {
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            throw new SecurityException("密钥碎片 Fragment3 缺失，无法解密缓存（首次启动时应自动初始化）");
        }

        try {
            byte[] aesKey = KeySharding.assembleKey(frag1, frag2, frag3);

            try {
                return decryptGCM(ciphertext, authTag, aesKey, hexToBytes(nonceHex));
            } finally {
                Arrays.fill(aesKey, (byte) 0);
            }
        } finally {
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            Arrays.fill(frag3, (byte) 0);
        }
    }

    /**
     * 内部 GCM 解密实现。
     * <p>
     * 将密文与认证标签拼接后，使用 Java 的 AES/GCM/NoPadding 进行解密和认证验证。
     *
     * @param ciphertext 密文字节数组（不含认证标签）
     * @param authTag    认证标签字节数组（16 字节）
     * @param aesKey     AES-256 密钥（32 字节）
     * @param nonce      GCM Nonce（12 字节）
     * @return 解密后的明文字符串
     * @throws Exception 解密或认证失败时抛出
     */
    private static String decryptGCM(byte[] ciphertext, byte[] authTag, byte[] aesKey, byte[] nonce) throws Exception {
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        // Java GCM 实现：需要将 ciphertext + authTag 拼接后传入 doFinal
        byte[] combined = new byte[ciphertext.length + authTag.length];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(authTag, 0, combined, ciphertext.length, authTag.length);

        try {
            byte[] decrypted = cipher.doFinal(combined);
            return new String(decrypted, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(combined, (byte) 0);
        }
    }

    /**
     * 安全计算 AES 密钥指纹（用于验证密钥一致性）。
     * <p>
     * 在内存中还原 AES 密钥，计算 SHA-256 指纹后立即清零。
     *
     * @param serverFingerprint 服务端返回的密钥指纹（AA:BB:CC:... 格式或纯十六进制）
     * @return 指纹是否匹配
     */
    public static boolean verifyAesFingerprintSecurely(String serverFingerprint) {
        byte[] frag1 = hexToBytes(BuildInfo.BUILD_SIGNATURE);
        byte[] frag2 = ThemeConfig.getThemeSeed();
        byte[] frag3 = null;

        try {
            frag3 = FragmentStore.loadFragment3();
            if (frag3 == null) {
                Log.warn("[SecureAesHelper] Fragment3 缺失，无法验证指纹");
                return false;
            }

            byte[] aesKey = KeySharding.assembleKey(frag1, frag2, frag3);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(aesKey);
                String localFp = CloudCrypto.bytesToHex(hash);

                // 将服务端指纹中的冒号去除，统一为纯十六进制格式后比较
                String normalizedServerFp = serverFingerprint.replace(":", "");
                return localFp.equalsIgnoreCase(normalizedServerFp);
            } finally {
                Arrays.fill(aesKey, (byte) 0);
            }
        } catch (Exception e) {
            Log.warn("[SecureAesHelper] AES 指纹验证异常: " + e.getMessage());
            return false;
        } finally {
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            if (frag3 != null) {
                Arrays.fill(frag3, (byte) 0);
            }
        }
    }

    /**
     * 安全计算 HMAC-SHA256 请求签名（Layer 2）。
     * <p>
     * HMAC 密钥也采用碎片化存储（hmac-frag1/2/3 在 HardcodedConfig 中），
     * 运行时 XOR 还原后计算签名，使用完毕立即清零。
     * <p>
     * 签名算法：
     * <pre>
     * 签名原文 = timestamp + apiKey + requestPath（拼接，无分隔符）
     * 签名算法 = HMAC-SHA256(签名原文, hmacSecret)
     * 签名结果 = 64位十六进制小写字符串
     * </pre>
     *
     * @param hmacFrag1Hex  HMAC 碎片1（十六进制字符串）
     * @param hmacFrag2Hex  HMAC 碎片2（十六进制字符串）
     * @param hmacFrag3Hex  HMAC 碎片3（十六进制字符串）
     * @param timestamp     秒级时间戳字符串
     * @param apiKey        API 访问密钥
     * @param requestPath   请求路径
     * @return 小写十六进制格式的 HMAC-SHA256 签名字符串
     * @throws Exception 签名计算失败时抛出
     */
    public static String signHmacSecurely(
            String hmacFrag1Hex, String hmacFrag2Hex, String hmacFrag3Hex,
            String timestamp, String apiKey, String requestPath) throws Exception {

        // XOR 还原 HMAC 密钥（结果为 hex 字符串）
        byte[] frag1 = hexToBytes(hmacFrag1Hex);
        byte[] frag2 = hexToBytes(hmacFrag2Hex);
        byte[] frag3 = hexToBytes(hmacFrag3Hex);

        byte[] hmacKeyBytes = KeySharding.assembleKey(frag1, frag2, frag3);

        try {
            // 将 XOR 还原后的字节转为 hex 字符串，用该 hex 字符串的 UTF-8 字节作为 HMAC 密钥
            // 服务端使用 hex 字符串形式（非原始字节）作为 HMAC 密钥
            String hmacSecretHex = CloudCrypto.bytesToHex(hmacKeyBytes);
            byte[] hmacKeyUtf8 = hmacSecretHex.getBytes(StandardCharsets.UTF_8);

            try {
                // 签名原文 = timestamp + apiKey + requestPath（拼接，无分隔符）
                String payload = timestamp + apiKey + requestPath;

                SecretKeySpec keySpec = new SecretKeySpec(hmacKeyUtf8, "HmacSHA256");
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(keySpec);

                byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
                return CloudCrypto.bytesToHex(hash);
            } finally {
                Arrays.fill(hmacKeyUtf8, (byte) 0);
            }
        } finally {
            // 立即清零 HMAC 密钥和碎片内存
            Arrays.fill(hmacKeyBytes, (byte) 0);
            Arrays.fill(frag1, (byte) 0);
            Arrays.fill(frag2, (byte) 0);
            Arrays.fill(frag3, (byte) 0);
        }
    }

    /**
     * 将十六进制字符串转换为字节数组。
     */
    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
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

    private SecureAesHelper() {
        // 禁止实例化
    }
}
