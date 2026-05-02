package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.exceptions.CloudConfigException;
import com.github.balloonupdate.mcpatch.client.logging.Log;
import com.github.balloonupdate.mcpatch.client.utils.Env;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 云端配置拉取器。
 * <p>
 * 实现六层安全防御体系下的「云端优先 → 缓存回退 → 本地回退」三级降级策略：
 * <ol>
 *   <li>Layer 1: RSA-PSS 签名验证 — 防篡改（动态获取公钥，PSS 填充方案）</li>
 *   <li>Layer 2: HMAC-SHA256 请求签名 — 防直接调用（碎片化密钥安全签名）</li>
 *   <li>Layer 3: 密钥碎片化 XOR 还原 — 防逆向（Fragment1=代码, Fragment2=代码, Fragment3=本地文件）</li>
 *   <li>Layer 4: AES-256-GCM 加密传输 — 防窃听（GCM 认证加密，认证标签单独响应头）</li>
 *   <li>Layer 5: 秒级时间戳防重放 — 服务端校验</li>
 *   <li>Layer 6: HTTPS 证书锁定 — 防中间人</li>
 * </ol>
 * <p>
 * 服务端变更（2024）：
 * <ul>
 *   <li>加密算法从 AES-128-CBC 升级为 AES-256-GCM</li>
 *   <li>RSA 签名从 PKCS#1 v1.5 升级为 RSASSA-PSS</li>
 *   <li>始终返回加密响应（无需 ?encrypt=true 参数）</li>
 *   <li>GCM 认证标签在单独的响应头 X-GCM-Tag 中，非追加在密文后</li>
 * </ul>
 */
public class CloudConfigFetcher {

    /**
     * 缓存文件魔数头（GCM 格式）
     */
    private static final String CACHE_MAGIC = "MCGC";

    private final String apiUrl;
    private final String apiKey;
    // Layer 3: HMAC 密钥碎片（不存储还原后的密钥，签名时通过 SecureAesHelper 安全还原）
    private final String hmacFrag1;
    private final String hmacFrag2;
    private final String hmacFrag3;
    private final String cacheFile;
    private final int cacheTtl;
    private final int timeout;
    private final boolean fallbackLocal;
    private final boolean verifySignature;
    private final String certFingerprint;

    /**
     * 从 cloud-config 配置创建 CloudConfigFetcher 实例。
     *
     * @param cloudConfig AppConfig.CloudConfig 实例
     * @throws CloudConfigException 配置不完整时抛出
     */
    public CloudConfigFetcher(AppConfig.CloudConfig cloudConfig) throws CloudConfigException {
        this.apiUrl = cloudConfig.apiUrl;
        this.apiKey = cloudConfig.apiKey;
        this.cacheFile = cloudConfig.cacheFile;
        this.cacheTtl = cloudConfig.cacheTtl;
        this.timeout = cloudConfig.timeout;
        this.fallbackLocal = cloudConfig.fallbackLocal;
        this.verifySignature = cloudConfig.verifySignature;
        this.certFingerprint = cloudConfig.certFingerprint;

        // Layer 3: 存储 HMAC 碎片（签名时安全还原，不存储完整密钥）
        this.hmacFrag1 = cloudConfig.hmacFrag1;
        this.hmacFrag2 = cloudConfig.hmacFrag2;
        this.hmacFrag3 = cloudConfig.hmacFrag3;

        // 验证 HMAC 碎片完整性
        if (hmacFrag1 != null && !hmacFrag1.isEmpty()
                && hmacFrag2 != null && !hmacFrag2.isEmpty()
                && hmacFrag3 != null && !hmacFrag3.isEmpty()) {
            Log.debug("[CloudConfig] HMAC 碎片配置完整，请求签名已就绪");
        } else {
            Log.warn("[CloudConfig] HMAC 碎片不完整，将以无签名模式发送请求");
        }

        // 验证 AES Fragment3 是否已初始化
        if (!FragmentStore.isFragment3Initialized()) {
            Log.warn("[CloudConfig] AES Fragment3 未初始化，首次启动需运行初始化工具");
        }
    }

    /**
     * 拉取配置的主入口方法。
     * <p>
     * 实现三级降级策略：
     * 1. 尝试 fetchFromServer()，成功则保存缓存并返回明文 YAML
     * 2. 服务端请求失败时尝试 loadCache() 读取本地缓存
     * 3. 缓存也不可用时，根据 fallbackLocal 决定回退或抛异常
     *
     * @return 明文 YAML 字符串，null 表示使用本地配置
     * @throws CloudConfigException 云端配置获取失败且无法回退时抛出
     */
    public String fetch() throws CloudConfigException {
        Log.info("[CloudConfig] 正在从云端拉取配置...");

        // 1. 尝试从云端拉取
        try {
            String yaml = fetchFromServer();

            // 验证 YAML 格式有效性
            if (yaml != null && !yaml.trim().isEmpty() && isValidYaml(yaml)) {
                Log.info("[CloudConfig] 云端配置获取成功");
                return yaml;
            } else {
                Log.warn("[CloudConfig] 云端返回的 YAML 格式无效，尝试回退");
            }
        } catch (CloudConfigException e) {
            Log.warn("[CloudConfig] 云端配置获取失败: " + e.getMessage());
        } catch (Exception e) {
            Log.warn("[CloudConfig] 云端请求异常: " + e.getMessage());
        }

        // 2. 尝试本地缓存
        try {
            String cached = loadCache();
            if (cached != null && !cached.trim().isEmpty()) {
                Log.info("[CloudConfig] 云端不可达，使用本地缓存");
                return cached;
            }
        } catch (CloudConfigException e) {
            Log.warn("[CloudConfig] 缓存解密失败，已清除损坏的缓存文件: " + e.getMessage());
        }

        // 3. 回退策略
        if (fallbackLocal) {
            Log.info("[CloudConfig] 云端和缓存均不可用，回退到本地配置");
            return null;
        } else {
            throw new CloudConfigException("云端配置不可用且不允许本地回退");
        }
    }

    /**
     * 构建 OkHttpClient，可选配置 Layer 6 证书锁定。
     *
     * @return OkHttpClient 实例
     */
    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS);

        // Layer 6: HTTPS 证书锁定
        if (certFingerprint != null && !certFingerprint.isEmpty()) {
            try {
                URI uri = new URI(apiUrl);
                String host = uri.getHost();
                if (host != null && !host.isEmpty()) {
                    byte[] fingerprintBytes = CloudCrypto.hexToBytes(certFingerprint);
                    String pin = "sha256/" + Base64.getEncoder().encodeToString(fingerprintBytes);

                    builder.certificatePinner(new okhttp3.CertificatePinner.Builder()
                            .add(host, pin)
                            .build());

                    Log.debug("[CloudConfig] Layer 6 证书锁定已启用: " + host + " -> " + pin);
                }
            } catch (Exception e) {
                Log.warn("[CloudConfig] 证书锁定配置失败: " + e.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * Layer 1: 从 /api/security/public-key 动态获取 RSA 公钥。
     * <p>
     * 公钥不是硬编码在配置文件中，而是每次启动时从服务端获取，
     * 支持服务端密钥轮换。
     *
     * @param client OkHttpClient 实例
     * @return PEM 格式的 RSA 公钥，获取失败时返回 null
     */
    private String fetchRSAPublicKey(OkHttpClient client) {
        try {
            URI uri = new URI(apiUrl);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() > 0 && uri.getPort() != 443 && uri.getPort() != 80) {
                baseUrl += ":" + uri.getPort();
            }
            String publicKeyUrl = baseUrl + "/api/security/public-key";

            // 提取请求路径（用于 HMAC 签名）
            String requestPath = "/api/security/public-key";

            Request.Builder reqBuilder = new Request.Builder()
                    .url(publicKeyUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "Mcpatch2JavaClient/" + Env.getVersion());

            // Layer 2 + Layer 5: HMAC-SHA256 请求签名 + 秒级时间戳（与 fetchFromServer 一致）
            if (hmacFrag1 != null && !hmacFrag1.isEmpty()
                    && hmacFrag2 != null && !hmacFrag2.isEmpty()
                    && hmacFrag3 != null && !hmacFrag3.isEmpty()) {
                try {
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String signature = SecureAesHelper.signHmacSecurely(
                            hmacFrag1, hmacFrag2, hmacFrag3,
                            timestamp, apiKey, requestPath);

                    reqBuilder.header("X-Timestamp", timestamp);
                    reqBuilder.header("X-Signature", signature);

                    Log.debug("[CloudConfig] Layer 2+5: RSA公钥请求 HMAC签名已附加, path=" + requestPath);
                } catch (Exception e) {
                    Log.warn("[CloudConfig] RSA公钥请求 HMAC签名失败: " + e.getMessage());
                }
            }

            Request request = reqBuilder.build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.warn("[CloudConfig] 获取RSA公钥失败: HTTP " + response.code());
                    return null;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    Log.warn("[CloudConfig] 获取RSA公钥失败: 响应体为空");
                    return null;
                }

                String responseBody = body.string();
                JSONObject json = new JSONObject(responseBody);
                String publicKey = json.getString("publicKey");

                if (publicKey == null || publicKey.isEmpty()) {
                    Log.warn("[CloudConfig] 获取RSA公钥失败: publicKey 字段为空");
                    return null;
                }

                Log.info("[CloudConfig] Layer 1: RSA公钥动态获取成功");
                return publicKey;
            }
        } catch (Exception e) {
            Log.warn("[CloudConfig] 获取RSA公钥异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从云端服务器拉取加密配置。
     * <p>
     * 完整安全请求流程：
     * 1. Layer 2 + 5: 安全计算 HMAC-SHA256 请求签名 + 秒级时间戳
     * 2. 发送 HTTPS 请求（服务端始终返回加密响应，无需 ?encrypt=true）
     * 3. Layer 4: 安全解密 AES-256-GCM 响应（密钥仅方法作用域内存在，认证标签从单独响应头获取）
     * 4. Layer 1: 验证 RSA-PSS 签名（动态获取公钥）
     *
     * @return 解密后的明文 YAML 字符串
     * @throws Exception            网络请求失败
     * @throws CloudConfigException 解密失败或服务端返回错误
     */
    private String fetchFromServer() throws Exception, CloudConfigException {
        long startTime = System.currentTimeMillis();

        OkHttpClient client = buildClient();

        String version = Env.getVersion();
        // 请求加密响应（服务端需要 ?encrypt=true 参数才返回加密内容）
        String requestUrl = apiUrl;
        if (!requestUrl.contains("?")) {
            requestUrl += "?encrypt=true";
        } else if (!requestUrl.contains("encrypt=true")) {
            requestUrl += "&encrypt=true";
        }

        // 提取请求路径（用于 HMAC 签名，不包含查询参数）
        URI uri = new URI(apiUrl);
        String requestPath = uri.getPath();
        if (requestPath == null || requestPath.isEmpty()) {
            requestPath = "/";
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(requestUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "Mcpatch2JavaClient/" + version);

        // Layer 2 + Layer 5: HMAC-SHA256 请求签名 + 秒级时间戳
        if (hmacFrag1 != null && !hmacFrag1.isEmpty()
                && hmacFrag2 != null && !hmacFrag2.isEmpty()
                && hmacFrag3 != null && !hmacFrag3.isEmpty()) {
            try {
                // Layer 5: 秒级时间戳
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

                // Layer 2: 安全 HMAC-SHA256 签名（碎片还原 → 签名 → 清零，一步完成）
                String signature = SecureAesHelper.signHmacSecurely(
                        hmacFrag1, hmacFrag2, hmacFrag3,
                        timestamp, apiKey, requestPath);

                reqBuilder.header("X-Timestamp", timestamp);
                reqBuilder.header("X-Signature", signature);

                Log.debug("[CloudConfig] Layer 2+5: HMAC签名已附加 (安全模式), path=" + requestPath + ", timestamp=" + timestamp);
            } catch (Exception e) {
                Log.warn("[CloudConfig] HMAC请求签名失败，将以无签名模式发送请求: " + e.getMessage());
            }
        }

        Request request = reqBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                int code = response.code();
                String errMsg = "服务端返回错误: " + code;

                if (code == 403) {
                    errMsg += "（API密钥无效或签名验证失败，请检查配置）";
                } else if (code == 404) {
                    errMsg += "（暂无可用配置）";
                } else if (code >= 500) {
                    errMsg += "（服务器错误）";
                }

                throw new CloudConfigException(errMsg);
            }

            String result = decryptResponse(response, client);

            long elapsed = System.currentTimeMillis() - startTime;
            Log.info("[CloudConfig] 云端配置获取成功 (耗时: " + elapsed + "ms)");

            return result;
        }
    }

    /**
     * 解密 HTTP 响应（Layer 4 + Layer 1）。
     * <p>
     * 流程：
     * 1. Layer 4: 提取 X-AES-Nonce（或 X-AES-IV）、X-GCM-Tag、X-AES-Fingerprint 等响应头
     * 2. Layer 4: 安全验证 AES 密钥指纹
     * 3. Layer 4: 通过 SecureAesHelper 安全解密 AES-256-GCM（认证标签从单独响应头获取）
     * 4. Layer 1: 验证 RSA-PSS 签名（动态获取公钥）
     * 5. 保存加密缓存
     *
     * @param response OkHttp 响应对象
     * @param client   OkHttpClient 实例（用于 Layer 1 获取公钥）
     * @return 解密后的明文 YAML 字符串
     * @throws CloudConfigException 解密失败或缺少必要的响应头
     */
    private String decryptResponse(Response response, OkHttpClient client) throws CloudConfigException {
        // 1. 提取响应头
        // GCM Nonce：优先检查 X-AES-Nonce，兼容旧版 X-AES-IV
        String nonceHex = response.header("X-AES-Nonce");
        if (nonceHex == null || nonceHex.isEmpty()) {
            nonceHex = response.header("X-AES-IV");
        }

        // GCM 认证标签：优先从响应头获取，兼容服务器追加在密文末尾的情况
        String gcmAuthTagHex = response.header("X-AES-AuthTag");
        if (gcmAuthTagHex == null || gcmAuthTagHex.isEmpty()) {
            gcmAuthTagHex = response.header("x-aes-authtag");
        }
        if (gcmAuthTagHex == null || gcmAuthTagHex.isEmpty()) {
            gcmAuthTagHex = response.header("X-GCM-Tag");
        }

        String fingerprint = response.header("X-AES-Fingerprint");
        String configVersion = response.header("X-Config-Version");
        String configSignature = response.header("X-Config-Signature");

        if (nonceHex == null || nonceHex.isEmpty()) {
            throw new CloudConfigException("缺少 X-AES-Nonce/X-AES-IV 响应头");
        }

        // 2. 读取响应体
        ResponseBody body = response.body();
        if (body == null) {
            throw new CloudConfigException("响应体为空");
        }

        byte[] encryptedRaw;
        try {
            encryptedRaw = body.bytes();
        } catch (Exception e) {
            throw new CloudConfigException("读取响应体失败: " + e.getMessage());
        }

        if (encryptedRaw.length == 0) {
            throw new CloudConfigException("响应体为空");
        }

        // 3. 判断 GCM 认证标签来源和响应体格式
        // 服务器可能有两种格式：
        //   格式A: Auth tag 在单独响应头中，body 为 Base64 编码密文（不含 tag）
        //   格式B: Auth tag 追加在 body 末尾（原始二进制，最后16字节为 tag）
        boolean authTagInHeader = (gcmAuthTagHex != null && !gcmAuthTagHex.isEmpty());
        boolean bodyIsRawBinary = false;
        byte[] ciphertextOnly = null;

        if (!authTagInHeader && encryptedRaw.length > 16) {
            // 格式B: 从 body 末尾提取 16 字节 auth tag
            byte[] authTagBytes = Arrays.copyOfRange(encryptedRaw, encryptedRaw.length - 16, encryptedRaw.length);
            gcmAuthTagHex = CloudCrypto.bytesToHex(authTagBytes);
            ciphertextOnly = Arrays.copyOfRange(encryptedRaw, 0, encryptedRaw.length - 16);
            bodyIsRawBinary = true;
            Log.debug("[CloudConfig] GCM 认证标签从响应体末尾提取（16字节）");
        }

        if (gcmAuthTagHex == null || gcmAuthTagHex.isEmpty()) {
            throw new CloudConfigException("缺少 GCM 认证标签（响应头和响应体末尾均未找到）");
        }

        // 4. Layer 4: 安全验证 AES 密钥指纹
        if (fingerprint != null && !fingerprint.isEmpty()) {
            if (!SecureAesHelper.verifyAesFingerprintSecurely(fingerprint)) {
                throw new CloudConfigException("AES 密钥指纹不匹配，密钥可能已更换");
            }
            Log.debug("[CloudConfig] Layer 4: AES 密钥指纹验证通过");
        }

        // 5. Layer 4: 通过 SecureAesHelper 安全解密 AES-256-GCM
        String decryptedYaml;
        try {
            if (bodyIsRawBinary && ciphertextOnly != null) {
                // 原始二进制格式：密文直接是原始字节，需 Base64 编码后传入 decryptConfig
                String encryptedBase64 = Base64.getEncoder().encodeToString(ciphertextOnly);
                decryptedYaml = SecureAesHelper.decryptConfig(encryptedBase64, nonceHex, gcmAuthTagHex);
            } else {
                // Base64 格式：body 为 Base64 编码的密文字符串
                String encryptedBase64 = new String(encryptedRaw, StandardCharsets.UTF_8).trim();
                decryptedYaml = SecureAesHelper.decryptConfig(encryptedBase64, nonceHex, gcmAuthTagHex);
            }
        } catch (SecurityException e) {
            throw new CloudConfigException("密钥碎片缺失: " + e.getMessage());
        } catch (CloudConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudConfigException("AES-256-GCM 解密失败: " + e.getMessage());
        }

        // 5. Layer 1: RSA-PSS 签名验证（可选）
        if (verifySignature) {
            if (configSignature != null && !configSignature.isEmpty()) {
                // 动态获取 RSA 公钥
                String publicKeyPem = fetchRSAPublicKey(client);

                if (publicKeyPem != null && !publicKeyPem.isEmpty()) {
                    boolean valid = CloudCrypto.verifyRSASignature(decryptedYaml, configSignature, publicKeyPem);
                    if (!valid) {
                        throw new CloudConfigException("RSA-PSS 签名验证失败，配置内容可能被篡改，拒绝使用");
                    }
                    Log.info("[CloudConfig] Layer 1: RSA-PSS 签名验证通过");
                } else {
                    Log.warn("[CloudConfig] RSA 签名验证已启用但无法获取公钥，跳过验证");
                }
            } else {
                Log.warn("[CloudConfig] RSA 签名验证已启用但服务端未返回签名，跳过验证");
            }
        }

        // 6. 保存缓存（GCM 格式：MCGC + Nonce + AuthTag + Ciphertext）
        saveCache(encryptedRaw, nonceHex, gcmAuthTagHex, configVersion);

        return decryptedYaml;
    }

    /**
     * 将加密配置保存到本地缓存文件（AES-256-GCM 格式）。
     * <p>
     * 缓存文件采用二进制格式：
     * ┌────────────┬───────────────┬───────────────┬──────────────────┐
     * │ Magic (4B) │ Nonce (12B)   │ AuthTag (16B) │ Ciphertext (变长) │
     * │ "MCGC"     │ GCM Nonce     │ GCM 认证标签  │ Base64密文数据    │
     * └────────────┴───────────────┴───────────────┴──────────────────┘
     *
     * @param encrypted       密文字节数组（Base64 编码的响应体，不含认证标签）
     * @param nonceHex        GCM Nonce 的十六进制字符串（12 字节 = 24 位 hex）
     * @param gcmAuthTagHex   GCM 认证标签的十六进制字符串（16 字节 = 32 位 hex）
     * @param configVersion   配置版本号
     */
    private void saveCache(byte[] encrypted, String nonceHex, String gcmAuthTagHex, String configVersion) {
        try {
            byte[] nonce = CloudCrypto.hexToBytes(nonceHex);
            byte[] authTag = CloudCrypto.hexToBytes(gcmAuthTagHex);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(CACHE_MAGIC.getBytes(StandardCharsets.UTF_8));  // 4 bytes magic
            baos.write(nonce);                                          // 12 bytes nonce
            baos.write(authTag);                                        // 16 bytes auth tag
            baos.write(encrypted);                                      // variable length Base64 ciphertext

            Files.write(Path.of(cacheFile), baos.toByteArray());

            // 写元数据
            Properties meta = new Properties();
            meta.setProperty("cached-at", String.valueOf(System.currentTimeMillis()));
            meta.setProperty("config-version", configVersion != null ? configVersion : "0");
            meta.setProperty("encryption", "AES-256-GCM");

            try (Writer w = Files.newBufferedWriter(Path.of(cacheFile + ".meta"))) {
                meta.store(w, null);
            }
        } catch (Exception e) {
            Log.warn("[CloudConfig] 缓存写入失败: " + e.getMessage());
        }
    }

    /**
     * 从本地缓存文件读取并解密配置（AES-256-GCM 格式）。
     * <p>
     * 通过 SecureAesHelper 安全解密，密钥仅在解密方法的作用域内存在。
     * <p>
     * 缓存格式：
     * ┌────────────┬───────────────┬───────────────┬──────────────────┐
     * │ Magic (4B) │ Nonce (12B)   │ AuthTag (16B) │ Ciphertext (变长) │
     * │ "MCGC"     │ GCM Nonce     │ GCM 认证标签  │ Base64密文数据    │
     * └────────────┴───────────────┴───────────────┴──────────────────┘
     *
     * @return 解密后的明文 YAML 字符串，null 表示缓存不可用
     * @throws CloudConfigException 解密失败
     */
    private String loadCache() throws CloudConfigException {
        File f = new File(cacheFile);
        if (!f.exists() || !isCacheValid()) {
            return null;
        }

        try {
            byte[] data = Files.readAllBytes(f.toPath());

            // 验证 Magic 头和最小长度（4 magic + 12 nonce + 16 authTag = 32 字节头）
            if (data.length < 32) {
                deleteCacheFiles();
                return null;
            }

            String magic = new String(data, 0, 4, StandardCharsets.UTF_8);

            // 支持两种缓存格式：MCGC (GCM) 和 MCPC (旧版 CBC)
            if (magic.equals(CACHE_MAGIC)) {
                // GCM 格式缓存
                byte[] nonce = Arrays.copyOfRange(data, 4, 16);        // 12 bytes nonce
                byte[] authTag = Arrays.copyOfRange(data, 16, 32);     // 16 bytes auth tag
                byte[] encryptedRaw = Arrays.copyOfRange(data, 32, data.length);  // Base64 ciphertext

                String nonceHex = CloudCrypto.bytesToHex(nonce);
                // 缓存中存储的是 Base64 编码的密文，解码后解密
                byte[] ciphertext = Base64.getDecoder().decode(encryptedRaw);

                return SecureAesHelper.decryptConfigBytes(ciphertext, authTag, nonceHex);
            } else if (magic.equals("MCPC")) {
                // 旧版 CBC 格式缓存，不再支持，清除并返回 null
                Log.warn("[CloudConfig] 检测到旧版 CBC 格式缓存，已清除");
                deleteCacheFiles();
                return null;
            } else {
                deleteCacheFiles();
                return null;
            }
        } catch (SecurityException e) {
            deleteCacheFiles();
            throw new CloudConfigException("缓存解密失败（碎片缺失）: " + e.getMessage());
        } catch (CloudConfigException e) {
            deleteCacheFiles();
            throw e;
        } catch (Exception e) {
            deleteCacheFiles();
            return null;
        }
    }

    /**
     * 检查本地缓存是否在有效期内。
     */
    private boolean isCacheValid() {
        File metaFile = new File(cacheFile + ".meta");
        if (!metaFile.exists()) {
            return false;
        }

        try {
            Properties meta = new Properties();
            meta.load(Files.newInputStream(metaFile.toPath()));
            long cachedAt = Long.parseLong(meta.getProperty("cached-at", "0"));
            return (System.currentTimeMillis() - cachedAt) < cacheTtl * 1000L;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除缓存文件和元数据文件。
     */
    private void deleteCacheFiles() {
        try {
            new File(cacheFile).delete();
            new File(cacheFile + ".meta").delete();
        } catch (Exception ignored) {
        }
    }

    /**
     * 验证 YAML 格式的基本有效性。
     */
    private boolean isValidYaml(String yaml) {
        try {
            Yaml ymlParser = new Yaml();
            Object parsed = ymlParser.load(yaml);

            if (!(parsed instanceof Map)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;

            return map.containsKey("urls");
        } catch (Exception e) {
            return false;
        }
    }
}
