package com.github.balloonupdate.mcpatch.client.config;

import com.github.balloonupdate.mcpatch.client.HardcodedConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件对象，保存所有从配置文件里读取出来的配置项
 */
public class AppConfig {
    /**
     * 更新服务器地址列表
     */
    public List<String> urls;

    /**
     * 记录客户端版本号文件的路径<p>
     * 客户端的版本号会被存储在这个文件里，并以此为依据判断是否更新到了最新版本
     */
    public String versionFilePath;

    /**
     * 当程序发生错误而更新失败时，是否可以继续进入游戏<p>
     * 如果为true，发生错误时会忽略错误，正常启动游戏，但是可能会因为某些新模组未下载无法进服<p>
     * 如果为false，发生错误时会直接崩溃掉Minecraft进程，停止游戏启动过程<p>
     * 此选项仅当程序以非图形模式启动时有效，因为在图形模式下，会主动弹框并将选择权交给用户
     */
    public boolean allowError;

    /**
     * 在没有更新时，是否显示“资源文件暂无更新!”提示框
     */
    public boolean showNoUpdateMessage;

    /**
     * 在有更新时，是否显示更新日志提示框
     */
    public boolean showHasUpdateMessage;

    /**
     * 自动关闭更新日志的时间，单位为毫秒。设置为0代表不会自动关闭更新日志窗口，需要手点
     */
    public int autoCloseChangelogs;

    /**
     * 安静模式，是否只在下载文件时才显示窗口<p>
     * 如果为true，程序启动后在后台静默检查文件更新，而不显示窗口，若没有更新会直接启动Minecraft，<p>
     *            有更新的话再显示下载进度条窗口，此选项可以尽可能将程序的存在感降低（适合线上环境）<p>
     * 如果为false，每次都正常显示窗口（适合调试环境）<p>
     * 此选项仅当程序以图形模式启动时有效
     */
    public boolean silentMode;

    /**
     * 禁用主题
     */
    public boolean disableTheme;

    /**
     * 窗口标题，可以自定义更新时的窗口标题<p>
     * 只有在桌面环境上时才有效，因为非桌面环境没法弹出窗口
     */
    public String windowTitle;

    /**
     * 更新的起始目录，也就是要把文件都更新到哪个目录下<p>
     * 默认情况下程序会智能搜索，并将所有文件更新到.minecraft父目录下（也是启动主程序所在目录），<p>
     * 这样文件更新的位置就不会随主程序文件的工作目录变化而改变了，每次都会更新在相同目录下。<p>
     * 如果你不喜欢这个智能搜索的机制，可以修改此选项来把文件更新到别的地方（十分建议保持默认不要修改）<p>
     * 1. 当此选项的值是空字符串''时，会智能搜索.minecraft父目录作为更新起始目录（这也是默认值）<p>
     * 2. 当此选项的值是'.'时，会把当前工作目录作为更新起始目录<p>
     * 3. 当此选项的值是'..'时，会把当前工作目录的上级目录作为更新起始目录<p>
     * 4. 当此选项的值是别的时，比如'ab/cd'时，会把当前工作目录下的ab目录里面的cd目录作为更新起始目录
     */
    public String basePath;

    /**
     * 私有协议的超时判定时间，单位毫秒，值越小判定越严格<p>
     * 网络环境较差时可能会频繁出现连接超时，那么此时可以考虑增加此值（建议30s以下）<p>
     */
    public int privateTimeout;

    /**
     * 为http系的协议设置自定义headers
     */
    public Map<String, String> httpHeaders;

    /**
     * http系的协议连接超时判定时间，单位毫秒，值越小判定越严格<p>
     * 网络环境较差时可能会频繁出现连接超时，那么此时可以考虑增加此值（建议30s以下）
     */
    public int httpTimeout;

    /**
     * 出现网络问题时的重试次数，适用于所有协议，最大值不建议超过100<p>
     * 当服务器没有及时响应数据时，会消耗1次重试次数，然后进行重新连接<p>
     * 当所有的重试次数消耗完后，程序才会真正判定为超时，并弹出网络错误对话框<p>
     * 建议 timeout * retries 的总时间控制在20秒以内，避免玩家等的太久
     */
    public int reties;

    /**
     * 忽略对http系列协议的证书验证（如果开启了https）
     */
    public boolean ignoreSSLCertificate;


    /**
     * 是否忽略对http协议中content-length的校验
     */
    public boolean ignoreHttpContentLength;

    /**
     * 测试模式，开启后每次都会重头更新，会增加流量消耗，仅用来测试更新时网速
     */
    public boolean testMode;

    /**
     * 最大并行下载线程数，0 表示自动根据文件总大小决定<p>
     * 自动策略：&lt;1MB=1线程, 1-10MB=2线程, 10-50MB=3线程, 50-100MB=4线程, 100-500MB=6线程, &gt;500MB=8线程<p>
     * 线程数不会超过CPU核心数
     */
    public int maxThreads;

    /**
     * 分片下载的分片大小，单位字节，默认 1MB<p>
     * 仅当文件大小大于 5MB 时才会启用分片下载
     */
    public long chunkSize;

    /**
     * 分片下载的最大分片数，默认 16<p>
     * 避免小文件产生过多分片
     */
    public int maxChunks;

    /**
     * 是否启用分片下载，默认 true
     */
    public boolean enableChunkedDownload;

    /**
     * 是否启用防盗链鉴权（阿里云ESA A方案）<p>
     * 启用后，下载文件时会先向鉴权服务器请求 auth_key，然后拼接到下载 URL 中<p>
     * 仅对 HTTP/HTTPS 协议生效，私有协议(mcpatch://)不受影响
     */
    public boolean antiHotlinkEnabled;

    /**
     * 鉴权 API 地址<p>
     * 用于生成符合阿里云ESA A方案规范的鉴权密钥(auth_key)
     */
    public String authApiUrl;

    /**
     * 鉴权 URL 有效时长，单位秒<p>
     * 建议与CDN端配置的鉴权有效时长保持一致，默认3600秒（1小时）
     */
    public int authExpireTime;

    /**
     * 鉴权用户ID<p>
     * 根据业务需求设置，默认为0
     */
    public String authUid;

    /**
     * 云端配置相关参数<p>
     * 当 cloudConfig.enabled 为 false 或该字段为 null 时，云端配置拉取逻辑将被完全跳过
     */
    public CloudConfig cloudConfig;

    public AppConfig(Map<String, Object> map) {
        List<String> urls = getList(map, "urls", null, new ArrayList<>());
        String versionFilePath = getString(map, "version-file-path", null, "version-label.txt");
        boolean allowError = getBoolean(map, "allow-error", null, false);
        boolean showNoUpdateMessage = getBoolean(map, "show-no-update-message", "show-finish-message", true);
        boolean showHasUpdateMessage = getBoolean(map, "show-has-update-message", "show-finish-message", true);
        int autoCloseChangelogs = getInt(map, "auto-close-changelogs", null, 0);
        boolean silentMode = getBoolean(map, "silent-mode", null, false);
        boolean disableTheme = getBoolean(map, "disable-theme", null, false);
        String windowTitle = getString(map, "window-title", "changelogs_window_title", "Mcpatch");
        String basePath = getString(map, "base-path", null, "");
        int privateTimeout = getInt(map, "private-timeout", null, 7000);
        Map<String, String> httpHeaders = getMap(map, "http-headers", null, new HashMap<>());
        int httpTimeout = getInt(map, "http-timeout", null, 7000);
        int reties = getInt(map, "retries", "http-retries", 3);
        boolean ignoreSSLCertificate = getBoolean(map, "ignore-ssl-cert", "http-ignore-certificate", false);
        boolean ignoreHttpContentLength = getBoolean(map, "ignore-http-content-length", "", false);
        boolean testMode = getBoolean(map, "test-mode", null, false);
        int maxThreads = getInt(map, "max-threads", null, 0);
        long chunkSize = getLong(map, "chunk-size", null, 1024L * 1024);
        int maxChunks = getInt(map, "max-chunks", null, 16);
        boolean enableChunkedDownload = getBoolean(map, "enable-chunked-download", null, true);
        boolean antiHotlinkEnabled = getBoolean(map, "anti-hotlink-enabled", null, true);
        String authApiUrl = getString(map, "anti-hotlink-auth-url", null, "https://auth-api.mxzysoa.com/generate-auth-url");
        int authExpireTime = getInt(map, "anti-hotlink-expire-time", null, 3600);
        String authUid = getString(map, "anti-hotlink-uid", null, "0");

        // 解析 cloud-config 段
        CloudConfig cloudCfg = parseCloudConfig(map);

//        if (urls.contains("webda"))
//

        this.urls = urls;
        this.versionFilePath = versionFilePath;
        this.allowError = allowError;
        this.showNoUpdateMessage = showNoUpdateMessage;
        this.showHasUpdateMessage = showHasUpdateMessage;
        this.autoCloseChangelogs = autoCloseChangelogs;
        this.silentMode = silentMode;
        this.windowTitle = windowTitle;
        this.disableTheme = disableTheme;
        this.basePath = basePath;
        this.privateTimeout = privateTimeout;
        this.httpHeaders = httpHeaders;
        this.httpTimeout = httpTimeout;
        this.reties = reties;
        this.ignoreSSLCertificate = ignoreSSLCertificate;
        this.ignoreHttpContentLength = ignoreHttpContentLength;
        this.testMode = testMode;
        this.maxThreads = maxThreads;
        this.chunkSize = chunkSize;
        this.maxChunks = maxChunks;
        this.enableChunkedDownload = enableChunkedDownload;
        this.antiHotlinkEnabled = antiHotlinkEnabled;
        this.authApiUrl = authApiUrl;
        this.authExpireTime = authExpireTime;
        this.authUid = authUid;
        this.cloudConfig = cloudCfg;
    }

    @SuppressWarnings("unchecked")
    static <T> T getOption(Map<String, Object> map, String key, String alterKey, T defaultValue, Class<T> clazz) {
        Object value = map.get(key);

        if (value == null) {
            value = map.get(alterKey);
        }

        if (value == null) {
            return defaultValue;
        }

        if (!clazz.isInstance(value)) {
            throw new RuntimeException("配置文件中找到 " + key + " 配置项了，但是配置项的类型不匹配。预期 " + clazz.getSimpleName() + " ，实际是 " + value.getClass().getSimpleName());
        }

        return clazz.isInstance(value) ? (T) value : null;
    }

    static String getString(Map<String, Object> map, String key, String formerKey, String defaultValue) {
        return getOption(map, key, formerKey, defaultValue, String.class);
    }

    static boolean getBoolean(Map<String, Object> map, String key, String formerKey, boolean defaultValue) {
        return getOption(map, key, formerKey, defaultValue, Boolean.class);
    }

    static int getInt(Map<String, Object> map, String key, String formerKey, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(formerKey);
        }
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new RuntimeException("配置文件中找到 " + key + " 配置项了，但是配置项的类型不匹配。预期 Integer ，实际是 " + value.getClass().getSimpleName());
    }

    static long getLong(Map<String, Object> map, String key, String formerKey, long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(formerKey);
        }
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new RuntimeException("配置文件中找到 " + key + " 配置项了，但是配置项的类型不匹配。预期 Long ，实际是 " + value.getClass().getSimpleName());
    }

    static List<String> getList(Map<String, Object> map, String key, String formerKey, List<String> defaultValue) {
        return getOption(map, key, formerKey, defaultValue, List.class);
    }

    static Map<String, String> getMap(Map<String, Object> map, String key, String formerKey, Map<String, String> defaultValue) {
        Map<String, String> result = getOption(map, key, formerKey, defaultValue, Map.class);

        return result != null ? result : new HashMap<>();
    }

/**
     * 构建 CloudConfig 实例。
     * <p>
     * 云端配置的所有敏感值（API地址、密钥、HMAC碎片等）
     * 均从 {@link com.github.balloonupdate.mcpatch.client.HardcodedConfig} 硬编码读取，
     * 不再从 YAML 配置文件中解析，防止敏感数据以明文形式存储在外部文件中。
     * <p>
     * YAML 中的 cloud-config 段如果存在，仅作为开关覆盖（enabled 字段），
     * 其余字段均被忽略。
     *
     * @param map 顶层配置映射（仅用于检查 cloud-config.enabled 覆盖开关）
     * @return CloudConfig 实例，如果硬编码配置未启用且 YAML 也未启用则返回 null
     */
    @SuppressWarnings("unchecked")
    private static CloudConfig parseCloudConfig(Map<String, Object> map) {
        // 所有云端配置均从 HardcodedConfig 读取（XOR+LCG 编码存储，防泄露）
        CloudConfig cfg = new CloudConfig();

        // 检查 YAML 中是否有 cloud-config.enabled 覆盖开关
        // 如果 YAML 中显式设置 cloud-config.enabled: false，则禁用云端配置
        Object cloudObj = map.get("cloud-config");
        if (cloudObj instanceof Map) {
            Map<String, Object> cloudMap = (Map<String, Object>) cloudObj;
            Boolean yamlEnabled = getOption(cloudMap, "enabled", null, null, Boolean.class);
            if (yamlEnabled != null && !yamlEnabled) {
                // YAML 显式禁用，尊重此设置
                return null;
            }
        }

        // 从 HardcodedConfig 读取所有配置值
        cfg.enabled = HardcodedConfig.isEnabled();
        cfg.apiUrl = HardcodedConfig.getApiUrl();
        cfg.apiKey = HardcodedConfig.getApiKey();
        cfg.cacheFile = HardcodedConfig.getCacheFile();
        cfg.cacheTtl = HardcodedConfig.getCacheTtl();
        cfg.timeout = HardcodedConfig.getTimeout();
        cfg.fallbackLocal = HardcodedConfig.isFallbackLocal();
        cfg.verifySignature = HardcodedConfig.isVerifySignature();
        cfg.certFingerprint = HardcodedConfig.getCertFingerprint();

        // Layer 3: HMAC 密钥碎片化存储（从 HardcodedConfig 编码读取）
        cfg.hmacFrag1 = HardcodedConfig.getHmacFrag1();
        cfg.hmacFrag2 = HardcodedConfig.getHmacFrag2();
        cfg.hmacFrag3 = HardcodedConfig.getHmacFrag3();

        return cfg;
    }

    /**
     * 云端配置内部类。
     * <p>
     * 所有字段值均由 {@link HardcodedConfig} 硬编码提供（XOR+LCG 编码存储），
     * 不再从 YAML 配置文件中读取，防止敏感数据以明文形式泄露。
     * <p>
     * YAML 中的 cloud-config 段仅保留 enabled 开关作为覆盖（设为 false 可禁用云端配置），
     * 其余字段（api-url、api-key、hmac-frag1/2/3 等）即使写在 YAML 中也会被忽略。
     * <p>
     * 密钥碎片化存储策略（Layer 3 防逆向）：<br>
     * AES 密钥碎片（3个XOR碎片，还原公式：aesKey = frag1 XOR frag2 XOR frag3）：<br>
     * &nbsp;&nbsp;Fragment1 → 嵌入 BuildInfo.BUILD_SIGNATURE（代码常量，伪装为构建签名）<br>
     * &nbsp;&nbsp;Fragment2 → 嵌入 ThemeConfig.THEME_ACCENT_COLORS（代码常量，伪装为主题色值）<br>
     * &nbsp;&nbsp;Fragment3 → 存储在本地隐藏文件 FragmentStore（.minecraft/assets/indexes/vXXXX）<br>
     * HMAC 密钥碎片（3个XOR碎片，还原公式：hmacSecret = frag1 XOR frag2 XOR frag3）：<br>
     * &nbsp;&nbsp;所有3个碎片均以 XOR+LCG 编码存储在 HardcodedConfig 中，运行时解码。
     */
    public static class CloudConfig {
        /**
         * 是否启用云端配置
         */
        public boolean enabled = false;

        /**
         * API地址，如 https://auth-config.mxzysoa.com/api/client
         */
        public String apiUrl = "";

        /**
         * API访问密钥
         */
        public String apiKey = "";

        /**
         * 本地缓存路径（相对运行目录）
         */
        public String cacheFile = ".mcpatch-config.enc";

        /**
         * 缓存有效期（秒）
         */
        public int cacheTtl = 3600;

        /**
         * HTTP请求超时（毫秒）
         */
        public int timeout = 7000;

        /**
         * 云端不可用时回退到本地
         */
        public boolean fallbackLocal = true;

        /**
         * 是否验证RSA-2048签名<p>
         * 启用后会从 /api/security/public-key 动态获取公钥，验证配置完整性
         */
        public boolean verifySignature = false;

        /**
         * HTTPS 证书指纹（SHA-256，64位hex，用于证书锁定 Layer 6）<p>
         * 为空时表示不启用证书锁定<p>
         * 格式：纯hex小写无冒号，如 3a7b2cd45e6f...
         */
        public String certFingerprint = "";

        /**
         * HMAC-SHA256 密钥碎片1（Layer 3 碎片化防逆向）<p>
         * 还原公式：hmacSecret = hmacFrag1 XOR hmacFrag2 XOR hmacFrag3<p>
         * 每个碎片为64位hex字符串（32字节）
         */
        public String hmacFrag1 = "";

        /**
         * HMAC-SHA256 密钥碎片2
         */
        public String hmacFrag2 = "";

        /**
         * HMAC-SHA256 密钥碎片3
         */
        public String hmacFrag3 = "";
    }
}