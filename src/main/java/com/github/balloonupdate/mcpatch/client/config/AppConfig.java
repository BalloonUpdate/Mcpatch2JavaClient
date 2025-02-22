package com.github.balloonupdate.mcpatch.client.config;

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
     * 在没有更新时，是否显示“资源文件暂无更新!”提示框<p>
     * 在有更新时，此选项不生效
     */
    public boolean showFinishMessage;

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
     * 测试模式，开启后每次都会重头更新，会增加流量消耗，仅用来测试更新时网速
     */
    public boolean testMode;


    public AppConfig(Map<String, Object> map) {
        List<String> urls = getList(map, "urls", null, new ArrayList<>());
        String versionFilePath = getString(map, "version-file-path", null, "version-label.txt");
        boolean allowError = getBoolean(map, "allow-error", null, false);
        boolean showFinishMessage = getBoolean(map, "show-finish-message", null, true);
        int autoCloseChangelogs = getInt(map, "auto-close-changelogs", null, 0);
        boolean silentMode = getBoolean(map, "silent-mode", null, false);
        boolean disableTheme = getBoolean(map, "disable-theme", null, false);
        String windowTitle = getString(map, "window-title", null, "Mcpatch");
        String basePath = getString(map, "base-path", null, "");
        int privateTimeout = getInt(map, "private-timeout", null, 7000);
        Map<String, String> httpHeaders = getMap(map, "http-headers", null, new HashMap<>());
        int httpTimeout = getInt(map, "http-timeout", null, 7000);
        int reties = getInt(map, "retries", "http-retries", 3);
        boolean ignoreSSLCertificate = getBoolean(map, "ignore-ssl-cert", "http-ignore-certificate", false);
        boolean testMode = getBoolean(map, "test-mode", null, false);

//        if (urls.contains("webda"))
//

        this.urls = urls;
        this.versionFilePath = versionFilePath;
        this.allowError = allowError;
        this.showFinishMessage = showFinishMessage;
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
        this.testMode = testMode;
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
        return getOption(map, key, formerKey, defaultValue, Integer.class);
    }

    static List<String> getList(Map<String, Object> map, String key, String formerKey, List<String> defaultValue) {
        return getOption(map, key, formerKey, defaultValue, List.class);
    }

    static Map<String, String> getMap(Map<String, Object> map, String key, String formerKey, Map<String, String> defaultValue) {
        Map<String, String> result = getOption(map, key, formerKey, defaultValue, Map.class);

        return result != null ? result : new HashMap<>();
    }
}