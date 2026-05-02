package com.github.balloonupdate.mcpatch.client;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 硬编码配置（防泄露）。
 * <p>
 * 所有配置值均以编码形式存储，运行时动态解码。
 * JAR 中不包含任何明文配置文件。
 */
public class HardcodedConfig {

    // 内部常量
    private static final int _K1 = 0x5A3C;
    private static final int _K2 = 0xF7E1;

    // 内部参数

    /** ε1 */
    private static final int[] _URLS_1 = {
            0xB5, 0x26, 0x57, 0x50, 0xAA, 0xA4, 0x50, 0x63,
            0xF1, 0xC6, 0xAA, 0x16, 0x7C, 0x0E, 0x0D, 0x9D,
            0x3E, 0x6D, 0x72, 0x7E, 0x2A, 0x21, 0x02, 0x53,
            0x68, 0x39, 0xFB, 0x09, 0xF5, 0x45, 0x0F
    };

    /** ε2 */
    private static final int[] _VERSION_FILE_PATH = {
            0xAB, 0x37, 0x51, 0x53, 0xB0, 0xF1, 0x11, 0x61,
            0xF9, 0xCB, 0xF9, 0x5D, 0x7D, 0x58, 0x03, 0x9C,
            0x39
    };

    /** ε3 */
    private static final int[] _WINDOW_TITLE = {
            0x90, 0x31, 0x53, 0x41, 0xAD, 0xFD, 0x17
    };

    /** ε4 */
    private static final int[] _API_URL = {
            0xB5, 0x26, 0x57, 0x50, 0xAA, 0xA4, 0x50, 0x63,
            0xF4, 0xDF, 0xEF, 0x50, 0x3C, 0x15, 0x18, 0x8A,
            0x2B, 0x6B, 0x74, 0x7E, 0x24, 0x36, 0x15, 0x05,
            0x76, 0x35, 0xEA, 0x46, 0xE2, 0x49, 0x0A, 0x3B,
            0xDC, 0xC2, 0x6A, 0xAF, 0xDA, 0x92, 0x36, 0xC9,
            0x1B, 0x7E
    };

    /** ε5 */
    private static final int[] _API_KEY = {
            0xB0, 0x31, 0x53, 0x41, 0xAD, 0xFD, 0x17, 0x13,
            0xF0, 0xCB, 0xAF, 0x09, 0x23, 0x46, 0x16, 0x87,
            0x7B, 0x60, 0x26, 0x33, 0x71, 0x7C, 0x56, 0x44,
            0x32, 0x6F, 0xEE, 0x58, 0xE2, 0x15, 0x02, 0x76,
            0x8A, 0xD3, 0x30, 0xB3, 0x8F, 0xCA, 0x6D, 0xCE,
            0x14, 0x39, 0x4B, 0xFA, 0x94, 0xEE, 0x67, 0x77,
            0x1F, 0x06, 0xCA, 0x86, 0x1D, 0x99, 0x2E, 0xE4
    };

    /** ε6 */
    private static final int[] _CACHE_FILE = {
            0xF3, 0x3F, 0x40, 0x50, 0xB8, 0xEA, 0x1C, 0x24,
            0xB8, 0xC9, 0xF4, 0x56, 0x77, 0x1F, 0x10, 0xCA,
            0x28, 0x6C, 0x70
    };

    /** ε7 */
    private static final int[] _HMAC_FRAG1 = {
            0xB9, 0x31, 0x40, 0x42, 0xE9, 0xA9, 0x1A, 0x78,
            0xA1, 0x92, 0xF9, 0x0D, 0x20, 0x44, 0x14, 0xDD,
            0x78, 0x63, 0x2A, 0x65, 0x7E, 0x79, 0x0C, 0x19,
            0x36, 0x39, 0xB2, 0x59, 0xE5, 0x43, 0x57, 0x76,
            0xDC, 0x85, 0x67, 0xB3, 0xDB, 0x9F, 0x67, 0xCF,
            0x45, 0x6F, 0x1A, 0xFA, 0x94, 0xE1, 0x65, 0x22,
            0x1A, 0x01, 0x92, 0x82, 0x4F, 0xCF, 0x76, 0xED,
            0xD6, 0x8B, 0x09, 0xAA, 0x58, 0xE4, 0x71, 0x10
    };

    /** ε8 */
    private static final int[] _HMAC_FRAG2 = {
            0xE5, 0x6B, 0x12, 0x18, 0xE0, 0xA7, 0x4D, 0x7E,
            0xAD, 0xC9, 0xAF, 0x0D, 0x75, 0x41, 0x43, 0xD0,
            0x2C, 0x64, 0x20, 0x34, 0x2D, 0x2C, 0x0B, 0x19,
            0x67, 0x6E, 0xBC, 0x0A, 0xB3, 0x1E, 0x5E, 0x26,
            0xDF, 0xD6, 0x33, 0xB8, 0xDA, 0xC6, 0x67, 0x95,
            0x17, 0x3D, 0x4F, 0xFD, 0x95, 0xB0, 0x65, 0x22,
            0x1C, 0x00, 0xC3, 0x81, 0x11, 0x97, 0x79, 0xE8,
            0x81, 0x8A, 0x08, 0xF1, 0x05, 0xE4, 0x76, 0x45
    };

    /** ε9 */
    private static final int[] _HMAC_FRAG3 = {
            0xE8, 0x60, 0x42, 0x10, 0xBD, 0xA8, 0x46, 0x29,
            0xA6, 0x9F, 0xA8, 0x5B, 0x27, 0x4E, 0x47, 0xD1,
            0x7A, 0x67, 0x70, 0x68, 0x7B, 0x2F, 0x0A, 0x1F,
            0x33, 0x3C, 0xEF, 0x51, 0xE4, 0x47, 0x51, 0x70,
            0x8C, 0x80, 0x33, 0xB1, 0x80, 0xC7, 0x6F, 0x9A,
            0x14, 0x3E, 0x4C, 0xFE, 0x95, 0xB0, 0x35, 0x76,
            0x1E, 0x55, 0xC1, 0xD4, 0x11, 0x9E, 0x77, 0xBA,
            0xDC, 0xDB, 0x5F, 0xFC, 0x04, 0xB6, 0x71, 0x4C
    };

    /** ε10 */
    private static final int[] _AUTH_API_URL = {
            0xB5, 0x26, 0x57, 0x50, 0xAA, 0xA4, 0x50, 0x63,
            0xF4, 0xDF, 0xEF, 0x50, 0x3C, 0x17, 0x07, 0x8D,
            0x63, 0x6F, 0x6B, 0x2A, 0x30, 0x3D, 0x00, 0x1D,
            0x2B, 0x39, 0xE4, 0x05, 0xAE, 0x41, 0x02, 0x7A,
            0xD8, 0xC0, 0x62, 0xF4, 0xDC, 0xD3, 0x3E, 0xD9,
            0x01, 0x62, 0x56, 0xED, 0x83, 0xBA
    };

    /** ε11 */
    private static final int[] _AUTH_UID = {
            0xED
    };

    /** ε12 — AES-256-GCM 密钥（64位hex = 32字节，用于静默初始化 Fragment3） */
    private static final int[] _AES_KEY = {
            0xBB, 0x62, 0x1A, 0x11, 0xE0, 0xFD, 0x49, 0x2A,
            0xF3, 0xC9, 0xAC, 0x5E, 0x28, 0x44, 0x45, 0xD1,
            0x78, 0x36, 0x21, 0x35, 0x2F, 0x7D, 0x0D, 0x1D,
            0x3C, 0x62, 0xB9, 0x51, 0xE4, 0x14, 0x01, 0x23,
            0x8C, 0x8B, 0x67, 0xB6, 0x81, 0x9C, 0x3E, 0x94,
            0x43, 0x3C, 0x18, 0xAE, 0xC1, 0xB3, 0x32, 0x71,
            0x14, 0x5B, 0xCB, 0xD3, 0x1A, 0xCC, 0x7F, 0xBA,
            0xD4, 0x8A, 0x59, 0xFD, 0x02, 0xE7, 0x71, 0x4C
    };

    // 常量

    private static final boolean _ALLOW_ERROR = false;
    private static final boolean _SHOW_NO_UPDATE_MESSAGE = true;
    private static final boolean _SHOW_HAS_UPDATE_MESSAGE = true;
    private static final int _AUTO_CLOSE_CHANGELOGS = 0;
    private static final boolean _SILENT_MODE = false;
    private static final boolean _DISABLE_THEME = false;
    private static final int _PRIVATE_TIMEOUT = 7000;
    private static final int _HTTP_TIMEOUT = 7000;
    private static final int _RETRIES = 3;
    private static final boolean _IGNORE_SSL_CERT = false;
    private static final boolean _IGNORE_HTTP_CONTENT_LENGTH = false;
    private static final boolean _TEST_MODE = false;
    private static final int _MAX_THREADS = 0;
    private static final long _CHUNK_SIZE = 1048576L;
    private static final int _MAX_CHUNKS = 16;
    private static final boolean _ENABLE_CHUNKED_DOWNLOAD = true;
    private static final boolean _ANTI_HOTLINK_ENABLED = true;
    private static final int _AUTH_EXPIRE_TIME = 3600;
    private static final int _CACHE_TTL = 3600;
    private static final int _TIMEOUT = 7000;
    private static final boolean _FALLBACK_LOCAL = true;
    private static final boolean _VERIFY_SIGNATURE = true;
    private static final boolean _ENABLED = true;

    // 内部处理

    private static String _d(int[] enc, int k1, int k2) {
        byte[] buf = new byte[enc.length];
        int key = k1 ^ k2;
        for (int i = 0; i < enc.length; i++) {
            buf[i] = (byte) (enc[i] ^ (key & 0xFF));
            key = (key * 1103515245 + 12345) & 0x7FFFFFFF;
        }
        String result = new String(buf, StandardCharsets.UTF_8);
        Arrays.fill(buf, (byte) 0);
        return result;
    }

    // ========================================
    // 本地配置公开接口
    // ========================================

    public static List<String> getUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(_d(_URLS_1, _K1, _K2));
        return urls;
    }

    public static String getVersionFilePath() {
        return _d(_VERSION_FILE_PATH, _K1, _K2);
    }

    public static boolean isAllowError() {
        return _ALLOW_ERROR;
    }

    public static boolean isShowNoUpdateMessage() {
        return _SHOW_NO_UPDATE_MESSAGE;
    }

    public static boolean isShowHasUpdateMessage() {
        return _SHOW_HAS_UPDATE_MESSAGE;
    }

    public static int getAutoCloseChangelogs() {
        return _AUTO_CLOSE_CHANGELOGS;
    }

    public static boolean isSilentMode() {
        return _SILENT_MODE;
    }

    public static boolean isDisableTheme() {
        return _DISABLE_THEME;
    }

    public static String getWindowTitle() {
        return _d(_WINDOW_TITLE, _K1, _K2);
    }

    public static String getBasePath() {
        return "";
    }

    public static int getPrivateTimeout() {
        return _PRIVATE_TIMEOUT;
    }

    public static Map<String, String> getHttpHeaders() {
        return new HashMap<>();
    }

    public static int getHttpTimeout() {
        return _HTTP_TIMEOUT;
    }

    public static int getRetries() {
        return _RETRIES;
    }

    public static boolean isIgnoreSslCert() {
        return _IGNORE_SSL_CERT;
    }

    public static boolean isIgnoreHttpContentLength() {
        return _IGNORE_HTTP_CONTENT_LENGTH;
    }

    public static boolean isTestMode() {
        return _TEST_MODE;
    }

    public static int getMaxThreads() {
        return _MAX_THREADS;
    }

    public static long getChunkSize() {
        return _CHUNK_SIZE;
    }

    public static int getMaxChunks() {
        return _MAX_CHUNKS;
    }

    public static boolean isEnableChunkedDownload() {
        return _ENABLE_CHUNKED_DOWNLOAD;
    }

    public static boolean isAntiHotlinkEnabled() {
        return _ANTI_HOTLINK_ENABLED;
    }

    public static String getAuthApiUrl() {
        return _d(_AUTH_API_URL, _K1, _K2);
    }

    public static int getAuthExpireTime() {
        return _AUTH_EXPIRE_TIME;
    }

    public static String getAuthUid() {
        return _d(_AUTH_UID, _K1, _K2);
    }

    // ========================================
    // 云端配置公开接口
    // ========================================

    public static String getApiUrl() {
        return _d(_API_URL, _K1, _K2);
    }

    public static String getApiKey() {
        return _d(_API_KEY, _K1, _K2);
    }

    public static String getCacheFile() {
        return _d(_CACHE_FILE, _K1, _K2);
    }

    public static int getCacheTtl() {
        return _CACHE_TTL;
    }

    public static int getTimeout() {
        return _TIMEOUT;
    }

    public static boolean isFallbackLocal() {
        return _FALLBACK_LOCAL;
    }

    public static boolean isVerifySignature() {
        return _VERIFY_SIGNATURE;
    }

    public static boolean isEnabled() {
        return _ENABLED;
    }

    public static String getCertFingerprint() {
        return "";
    }

    public static String getHmacFrag1() {
        return _d(_HMAC_FRAG1, _K1, _K2);
    }

    public static String getHmacFrag2() {
        return _d(_HMAC_FRAG2, _K1, _K2);
    }

    public static String getHmacFrag3() {
        return _d(_HMAC_FRAG3, _K1, _K2);
    }

    /**
     * 获取 AES-256 密钥的十六进制字符串（64位hex = 32字节）。
     * <p>
     * 用于首次启动时静默生成 Fragment3 文件。
     * 部署时需替换 _AES_KEY 为实际密钥的编码值。
     *
     * @return AES-256 密钥的十六进制字符串
     */
    public static String getAesKey() {
        return _d(_AES_KEY, _K1, _K2);
    }

    // ========================================
    // 构建 Map<String, Object> 供 AppConfig 使用
    // ========================================

    /**
     * 生成等价于 mcpatch.yml 内容的配置 Map。
     * <p>
     * 替代从 YAML 文件解析，所有值来自硬编码常量。
     * 云端配置 enabled 字段可通过参数覆盖。
     *
     * @param cloudEnabledOverride 是否覆盖云端配置启用状态，null 表示使用硬编码默认值
     * @return 完整的配置 Map，格式与 SnakeYAML 解析结果一致
     */
    public static Map<String, Object> buildConfigMap(Boolean cloudEnabledOverride) {
        Map<String, Object> map = new HashMap<>();

        map.put("urls", getUrls());
        map.put("version-file-path", getVersionFilePath());
        map.put("allow-error", isAllowError());
        map.put("show-no-update-message", isShowNoUpdateMessage());
        map.put("show-has-update-message", isShowHasUpdateMessage());
        map.put("auto-close-changelogs", getAutoCloseChangelogs());
        map.put("silent-mode", isSilentMode());
        map.put("disable-theme", isDisableTheme());
        map.put("window-title", getWindowTitle());
        map.put("base-path", getBasePath());
        map.put("private-timeout", getPrivateTimeout());
        map.put("http-headers", getHttpHeaders());
        map.put("http-timeout", getHttpTimeout());
        map.put("retries", getRetries());
        map.put("ignore-ssl-cert", isIgnoreSslCert());
        map.put("ignore-http-content-length", isIgnoreHttpContentLength());
        map.put("test-mode", isTestMode());
        map.put("max-threads", getMaxThreads());
        map.put("chunk-size", getChunkSize());
        map.put("max-chunks", getMaxChunks());
        map.put("enable-chunked-download", isEnableChunkedDownload());
        map.put("anti-hotlink-enabled", isAntiHotlinkEnabled());
        map.put("anti-hotlink-auth-url", getAuthApiUrl());
        map.put("anti-hotlink-expire-time", getAuthExpireTime());
        map.put("anti-hotlink-uid", getAuthUid());

        // cloud-config 段
        Map<String, Object> cloudMap = new HashMap<>();
        boolean cloudEnabled = cloudEnabledOverride != null ? cloudEnabledOverride : isEnabled();
        cloudMap.put("enabled", cloudEnabled);
        map.put("cloud-config", cloudMap);

        return map;
    }

    private HardcodedConfig() {
        // 禁止实例化
    }
}
