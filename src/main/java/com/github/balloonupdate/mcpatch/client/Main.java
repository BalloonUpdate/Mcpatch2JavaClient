package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.exceptions.CloudConfigException;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.logging.ConsoleHandler;
import com.github.balloonupdate.mcpatch.client.logging.FileHandler;
import com.github.balloonupdate.mcpatch.client.logging.Log;
import com.github.balloonupdate.mcpatch.client.logging.LogLevel;
import com.github.balloonupdate.mcpatch.client.ui.McPatchWindow;
import com.github.balloonupdate.mcpatch.client.utils.BytesUtils;
import com.github.balloonupdate.mcpatch.client.utils.DialogUtility;
import com.github.balloonupdate.mcpatch.client.utils.Env;
import com.github.kasuminova.GUI.SetupSwing;
import org.yaml.snakeyaml.Yaml;

import java.awt.*;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

public class Main {
    /**
     * 程序的启动方式
     */
    public enum StartMethod {
        /**
         * 作为独立进程启动
         */
        Standalone,

        /**
         * 作为 java agent lib 启动
         */
        JavaAgent,

        /**
         * 由 modloader 启动
         */
        ModLoader,
    }

    public static void main(String[] args) throws Throwable  {
        boolean graphicsMode = Desktop.isDesktopSupported();

        if (args.length > 0 && args[0].equals("windowless"))
            graphicsMode = false;

        AppMain(graphicsMode, StartMethod.Standalone, true, false);
    }

    public static void premain(String agentArgs, Instrumentation ins) throws Throwable  {
        boolean graphicsMode = Desktop.isDesktopSupported();

        if (agentArgs != null && agentArgs.equals("windowless"))
            graphicsMode = false;

        AppMain(graphicsMode, StartMethod.JavaAgent, true, false);
    }

    public static boolean modloader(boolean enableLogFile, boolean disableTheme) throws Throwable {
        boolean graphicsMode = Desktop.isDesktopSupported();

        return AppMain(graphicsMode, StartMethod.ModLoader, enableLogFile, disableTheme);
    }

    /**
     * McPatchClient主逻辑
     * @param graphicsMode 是否以图形模式启动（桌面环境通常以图形模式启动，安卓环境通常不以图形模式启动）
     * @param startMethod 程序的启动方式。
     * @param enableLogFile 是否写入日志文件
     * @param disableTheme 是否强制禁用主题
     * @return 有木有文件更新
     */
    static boolean AppMain(boolean graphicsMode, StartMethod startMethod, boolean enableLogFile, boolean disableTheme) throws Throwable {
        // 记录有无更新
        boolean hasUpdate = false;

        McPatchWindow window = null;

        try {
            // 初始化控制台日志系统
            InitConsoleLogging(graphicsMode, enableLogFile);

            // 准备各种目录
            Path progDir = getProgramDirectory();
            Path workDir = getWorkDirectory(progDir);
            AppConfig config = new AppConfig(readConfig(progDir.resolve("mcpatch.yml")));

            // 初始化文件日志系统（在云端配置拉取之前初始化，确保 CloudConfig 日志也写入 mcpatch.log）
            String logFileName = graphicsMode ? "mcpatch.log" : "mcpatch.log.txt";
            Path logFilePath = progDir.resolve(logFileName);

            if (enableLogFile)
                InitFileLogging(logFilePath);

            // 静默初始化 Fragment3（首次启动自动生成，客户无需手动操作）
            // 如果 Fragment3 本地文件不存在，从 HardcodedConfig 中的 AES 密钥计算并写入
            if (config.cloudConfig != null && config.cloudConfig.enabled) {
                ensureFragment3Initialized();
            }

            // 云端配置拉取（插入位置：创建 AppConfig 之后）
            // 如果启用了云端配置，先从云端拉取完整的运行时配置，再合并到基础配置中
            if (config.cloudConfig != null && config.cloudConfig.enabled) {
                try {
                    CloudConfigFetcher fetcher = new CloudConfigFetcher(config.cloudConfig);
                    String cloudYaml = fetcher.fetch();
                    if (cloudYaml != null && !cloudYaml.isEmpty()) {
                        // 用云端配置覆盖/合并到 baseConfig（保留 cloud-config 段）
                        AppConfig cloudConfig = parseYaml(cloudYaml);
                        mergeConfig(config, cloudConfig);
                        Log.info("[CloudConfig] 云端配置加载成功");
                    }
                } catch (CloudConfigException e) {
                    Log.warn("[CloudConfig] 云端配置获取失败: " + e.getMessage());
                } catch (Exception e) {
                    Log.warn("[CloudConfig] 云端配置处理异常: " + e.getMessage());
                }
            }

            Path baseDir = getUpdateDirectory(workDir, config);

            // 非独立进程启动时，使用标签标明日志所属模块
            if (startMethod == StartMethod.ModLoader || startMethod == StartMethod.JavaAgent)
                Log.setAppIdentifier(true);

            // 打印调试信息
            PrintEnvironmentInfo(graphicsMode, startMethod, baseDir, workDir);

            // 应用主题
            if (graphicsMode && !disableTheme && !config.disableTheme)
                SetupSwing.init();

            // 初始化UI
            window = graphicsMode ? new McPatchWindow() : null;

            // 初始化窗口
            if (window != null) {
                window.setTitleText(config.windowTitle);
                window.setLabelText("正在连接到更新服务器");
                window.setLabelSecondaryText("");

                // 弹出窗口
                if (!config.silentMode)
                    window.show();
            }

//            // 点击窗口的叉时停止更新任务
//            if (window != null) {
//                window.onWindowClosing = w -> {
//                    if (workThread.isAlive())
//                        workThread.interrupt();
//                };
//            }

            Work work = new Work();
            work.window = window;
            work.config = config;
            work.baseDir = baseDir;
            work.progDir = progDir;
            work.logFilePath = logFilePath;
            work.graphicsMode = graphicsMode;
            work.startMethod = startMethod;

            try {
                // 启动更新任务
                hasUpdate = work.run();
            } catch (McpatchBusinessException e) {
                boolean a = e.getCause() instanceof InterruptedException;
                boolean b = e.getCause() instanceof ClosedByInterruptException;

                if (!a && !b) {
                    // 打印异常日志
                    try {
                        Log.openIndent("Crash");
                        Log.error(e.toString());
                        Log.closeIndent();
                    } catch (Exception ex) {
                        System.out.println("------------------------");
                        System.out.println(ex);
                    }

                    // 图形模式下弹框显示错误
                    if (graphicsMode) {
                        boolean sp = startMethod == StartMethod.Standalone;

                        String errMsg = e.getMessage() != null ? e.getMessage() : "<No Exception Message>";
                        String errMessage = BytesUtils.stringBreak(errMsg, 80, "\n");
                        String title = "发生错误 " + Env.getVersion();
                        String content = errMessage + "\n";
                        content += !sp ? "点击\"是\"显示错误详情并停止启动Minecraft，" : "点击\"是\"显示错误详情并退出，";
                        content += !sp ? "点击\"否\"继续启动Minecraft" : "点击\"否\"直接退出程序";

                        boolean choice = DialogUtility.confirm(title, content);

                        if (!sp)
                        {
                            if (choice)
                            {
                                DialogUtility.error("错误详情 " + Env.getVersion(), e.toString());

                                throw e;
                            }
                        } else {
                            if (choice)
                                DialogUtility.error("错误详情 " + Env.getVersion(), e.toString());

                            throw e;
                        }
                    }
                } else {
                    Log.info("更新过程被用户打断！");
                }
            }
        } finally {
            if (window != null)
                window.destroy();

            if (startMethod != Main.StartMethod.Standalone)
                Log.info("continue to start Minecraft!");

            // if (startMethod == StartMethod.Standalone)
            //     Runtime.getRuntime().exit(0);
        }

        return hasUpdate;
    }

    /**
     * 获取Jar文件所在的目录
     */
    static Path getProgramDirectory()
    {
        if (Env.isDevelopment()) {
            String devWorkDir = System.getenv("MCPATCH_DEV_WORK_DIR");
            String devProgDir = System.getenv("MCPATCH_DEV_PROG_DIR");

            // 优先用环境变量里的
            if (devWorkDir != null && devProgDir != null) {
                return Paths.get(devProgDir);
            }

            // 然后用test文件夹
            Path userDir = Paths.get(System.getProperty("user.dir"));
            return userDir.resolve("test");
        }

        return Env.getJarPath().getParent();
    }

    /**
     * 获取进程的工作目录
     */
    static Path getWorkDirectory(Path progDir) {
        Path userDir = Paths.get(System.getProperty("user.dir"));

        if (Env.isDevelopment()) {
            String devWorkDir = System.getenv("MCPATCH_DEV_WORK_DIR");
            String devProgDir = System.getenv("MCPATCH_DEV_PROG_DIR");

            Path workDir;

            // 优先用环境变量里的
            if (devWorkDir != null && devProgDir != null) {
                workDir = Paths.get(devWorkDir);
            } else {
                // 同程序文件夹
                workDir = progDir;
            }

            try {
                Files.createDirectories(workDir);
                return workDir;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return userDir;
    }

    /**
     * 获取需要更新的起始目录
     * @param workDir 工作目录
     * @param config 配置信息
     * @return 更新起始目录
     * @throws McpatchBusinessException 当智能搜索搜不到.minecraft目录时
     */
    static Path getUpdateDirectory(Path workDir, AppConfig config) throws McpatchBusinessException {
        // 开发环境下直接返回工作目录
        if (Env.isDevelopment())
            return workDir;

        // 如果填写了base-path，就使用
        if (!config.basePath.equals("")) {
            return Env.getJarPath().getParent().resolve(config.basePath);
        }

        // 如果没有填写，就智能搜索
        Path result = searchDotMinecraft(workDir);

        // 必须找到才可以
        if (result == null) {
            String text = "找不到.minecraft目录。" +
                    "请将软件放到.minecraft目录的同级或者.minecraft目录下（最大7层深度）然后再次尝试运行。" +
                    "Windows系统下请不要使用右键的“打开方式”选择Java运行，而是要将Java设置成默认打开方式然后双击打开";
            throw new McpatchBusinessException(text);
        }

        return result;
    }

    /**
     * 向上搜索，直到有一个父目录包含 .minecraft 目录
     */
    static Path searchDotMinecraft(Path basedir) {
        try {
            File d = basedir.toFile();

            for (int i = 0; i < 7; i++) {
                for (File f : d.listFiles()) {
                    if (f.getName().equals(".minecraft")) {
                        return d.toPath();
                    }
                }

                d = d.getParentFile();
            }
        } catch (NullPointerException e) {
            return null;
        }

        return null;
    }

    // 从硬编码配置生成配置映射
    // 所有配置均已硬编码到 HardcodedConfig 中，不再从外部 YAML 文件读取
    static Map<String, Object> readConfig(Path external) {
        return HardcodedConfig.buildConfigMap(null);
    }

    /**
     * 静默初始化 Fragment3（首次启动自动生成）。
     * <p>
     * Fragment3 不在 JAR 内预置（安全隔离设计），但首次启动时需要自动生成，
     * 否则 AES 解密会因碎片缺失而失败。此方法检查本地是否存在 Fragment3 文件，
     * 若不存在则从 HardcodedConfig 中的 AES 密钥自动计算并写入，全程对用户无感。
     * <p>
     * 计算公式：Fragment3 = aesKey XOR Fragment1 XOR Fragment2
     */
    static void ensureFragment3Initialized() {
        // 检查是否需要重新初始化：首次初始化 或 密钥轮换后重新生成
        if (FragmentStore.isFragment3Initialized()) {
            // Fragment3 已存在，检查是否与当前 AES 密钥一致（密钥轮换检测）
            if (isFragment3KeyMatch()) {
                return; // 密钥一致，无需操作
            }
            // 密钥不匹配（密钥已轮换），删除旧 Fragment3 并重新初始化
            Log.info("[Main] 检测到 AES 密钥轮换，重新初始化 Fragment3");
            try {
                FragmentStore.removeFragment3();
            } catch (Exception e) {
                Log.warn("[Main] 删除旧 Fragment3 失败: " + e.getMessage());
            }
        }

        try {
            // 从 HardcodedConfig 读取完整的 AES 密钥
            String aesKeyHex = HardcodedConfig.getAesKey();
            if (aesKeyHex == null || aesKeyHex.isEmpty() || aesKeyHex.length() != 64) {
                Log.warn("[Main] AES 密钥未配置或格式无效，跳过 Fragment3 自动初始化");
                return;
            }

            // 读取 Fragment1 和 Fragment2
            byte[] frag1 = SecureAesHelper.hexToBytes(BuildInfo.BUILD_SIGNATURE);
            byte[] frag2 = ThemeConfig.getThemeSeed();
            byte[] fullAesKey = SecureAesHelper.hexToBytes(aesKeyHex);

            try {
                // 验证长度一致性
                if (frag1.length != 32 || frag2.length != 32 || fullAesKey.length != 32) {
                    Log.warn("[Main] 碎片长度不一致，跳过 Fragment3 自动初始化"
                            + " (frag1=" + frag1.length + ", frag2=" + frag2.length
                            + ", aesKey=" + fullAesKey.length + ")");
                    return;
                }

                // 计算 Fragment3 并写入本地文件
                FragmentStore.initFragment3(frag1, frag2, fullAesKey);
                Log.info("[Main] Fragment3 已自动初始化");
            } finally {
                Arrays.fill(frag1, (byte) 0);
                Arrays.fill(frag2, (byte) 0);
                Arrays.fill(fullAesKey, (byte) 0);
            }
        } catch (Exception e) {
            Log.warn("[Main] Fragment3 自动初始化失败: " + e.getMessage());
        }
    }

    /**
     * 检查现有 Fragment3 是否与当前 HardcodedConfig 中的 AES 密钥一致。
     * <p>
     * 通过比较两者还原出的 AES 密钥指纹来判断是否匹配。
     * 用于检测密钥轮换场景：当 ε12 更新后，旧的 Fragment3 应被替换。
     *
     * @return true 如果 Fragment3 还原出的密钥与当前 AES 密钥一致
     */
    static boolean isFragment3KeyMatch() {
        try {
            // 方法1：比较当前碎片还原的密钥指纹与 HardcodedConfig 中的密钥指纹
            byte[] frag1 = SecureAesHelper.hexToBytes(BuildInfo.BUILD_SIGNATURE);
            byte[] frag2 = ThemeConfig.getThemeSeed();
            byte[] frag3 = FragmentStore.loadFragment3();

            if (frag3 == null) {
                return false;
            }

            try {
                byte[] currentAesKey = KeySharding.assembleKey(frag1, frag2, frag3);
                try {
                    // 计算当前碎片还原出的密钥指纹
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] currentHash = digest.digest(currentAesKey);
                    String currentFp = CloudCrypto.bytesToHex(currentHash);

                    // 计算配置中的目标密钥指纹
                    String aesKeyHex = HardcodedConfig.getAesKey();
                    byte[] targetAesKey = SecureAesHelper.hexToBytes(aesKeyHex);
                    try {
                        byte[] targetHash = digest.digest(targetAesKey);
                        String targetFp = CloudCrypto.bytesToHex(targetHash);

                        boolean match = currentFp.equals(targetFp);
                        if (!match) {
                            Log.debug("[Main] Fragment3 密钥指纹不匹配"
                                    + " (current=" + currentFp.substring(0, 16)
                                    + ", target=" + targetFp.substring(0, 16) + ")");
                        }
                        return match;
                    } finally {
                        Arrays.fill(targetAesKey, (byte) 0);
                    }
                } finally {
                    Arrays.fill(currentAesKey, (byte) 0);
                }
            } finally {
                Arrays.fill(frag1, (byte) 0);
                Arrays.fill(frag2, (byte) 0);
                Arrays.fill(frag3, (byte) 0);
            }
        } catch (Exception e) {
            Log.debug("[Main] Fragment3 密钥匹配检查失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 初始化控制台日志系统
     */
    static void InitConsoleLogging(boolean graphicsMode, boolean enableLogFile) {
        LogLevel level;

        if (Env.isDevelopment()) {
            // 图形模式或者说禁用了日志文件，这时console就应该显示更详细的日志
            if (graphicsMode || !enableLogFile) {
                level = LogLevel.Debug;
            } else {
                level = LogLevel.Info;
            }
        } else {
            // 打包后也要显示详细一点的日志
            level = LogLevel.Debug;
        }

        Log.addHandler(new ConsoleHandler(level));
    }

    /**
     * 初始化文件日志系统
     */
    static void InitFileLogging(Path logFilePath) {
        Log.addHandler(new FileHandler(LogLevel.All, logFilePath));
    }

    /**
     * 收集并打印环境信息
     */
    static void PrintEnvironmentInfo(boolean graphicsMode, StartMethod startMethod, Path baseDir, Path workDir) {
        String jvmVersion = System.getProperty("java.version");
        String jvmVendor = System.getProperty("java.vendor");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");

        Log.info("已用内存: " + BytesUtils.convertBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        Log.info("图形模式: " + graphicsMode);
        Log.info("启动方式: " + startMethod);
        Log.info("基本目录: " + baseDir);
        Log.info("工作目录: " + workDir);
        Log.info("二进制文件目录: " + (Env.isDevelopment() ? "Dev" : Env.getJarPath()));
        Log.info("软件版本: " + Env.getVersion() + " (" + Env.getGitCommit() + ")");
        Log.info("虚拟机版本: " + jvmVendor + " (" + jvmVersion + ")");
        Log.info("操作系统: " + osName + ", " + osVersion + ", " + osArch);
    }

    /**
     * 解析 YAML 字符串为 AppConfig 对象。
     * 用于将云端拉取到的明文 YAML 配置转换为 AppConfig 实例。
     *
     * @param yaml YAML 格式的配置字符串
     * @return AppConfig 实例
     */
    static AppConfig parseYaml(String yaml) {
        Yaml ymlParser = new Yaml();
        Map<String, Object> map = ymlParser.load(yaml);
        return new AppConfig(map);
    }

    /**
     * 将云端配置合并到基础配置中。
     * <p>
     * 合并策略：云端下发的 YAML 是完整的运行时配置（包含 urls、version-file-path 等），
     * 直接用云端配置替换 baseConfig 中除 cloud-config 以外的所有字段。
     * cloud-config 段始终以本地内置版本为准，不会被云端配置覆盖。
     * 这样设计的好处是：云端可以完全控制运行时行为，而云端连接参数（密钥、地址）
     * 始终由开发者掌控，不会被云端配置覆盖。
     *
     * @param baseConfig  基础配置（本地内置 mcpatch.yml），合并结果直接修改此对象
     * @param cloudConfig 云端配置，用于覆盖基础配置中的运行时字段
     */
    static void mergeConfig(AppConfig baseConfig, AppConfig cloudConfig) {
        // 云端下发的配置覆盖本地配置（保留 cloud-config 段）
        baseConfig.urls = cloudConfig.urls;
        baseConfig.versionFilePath = cloudConfig.versionFilePath;
        baseConfig.allowError = cloudConfig.allowError;
        baseConfig.showNoUpdateMessage = cloudConfig.showNoUpdateMessage;
        baseConfig.showHasUpdateMessage = cloudConfig.showHasUpdateMessage;
        baseConfig.autoCloseChangelogs = cloudConfig.autoCloseChangelogs;
        baseConfig.silentMode = cloudConfig.silentMode;
        baseConfig.disableTheme = cloudConfig.disableTheme;
        baseConfig.windowTitle = cloudConfig.windowTitle;
        baseConfig.basePath = cloudConfig.basePath;
        baseConfig.privateTimeout = cloudConfig.privateTimeout;
        baseConfig.httpHeaders = cloudConfig.httpHeaders;
        baseConfig.httpTimeout = cloudConfig.httpTimeout;
        baseConfig.reties = cloudConfig.reties;
        baseConfig.ignoreSSLCertificate = cloudConfig.ignoreSSLCertificate;
        baseConfig.ignoreHttpContentLength = cloudConfig.ignoreHttpContentLength;
        baseConfig.testMode = cloudConfig.testMode;
        baseConfig.maxThreads = cloudConfig.maxThreads;
        baseConfig.chunkSize = cloudConfig.chunkSize;
        baseConfig.maxChunks = cloudConfig.maxChunks;
        baseConfig.enableChunkedDownload = cloudConfig.enableChunkedDownload;
        baseConfig.antiHotlinkEnabled = cloudConfig.antiHotlinkEnabled;
        baseConfig.authApiUrl = cloudConfig.authApiUrl;
        baseConfig.authExpireTime = cloudConfig.authExpireTime;
        baseConfig.authUid = cloudConfig.authUid;

        // cloudConfig 段始终以本地内置版本为准，不覆盖
        // baseConfig.cloudConfig 保持不变
    }
}
