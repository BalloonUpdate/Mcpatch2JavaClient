package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
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
import org.yaml.snakeyaml.parser.ParserException;

import java.awt.*;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

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

        if (agentArgs.equals("windowless"))
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
        final boolean[] hasUpdate = {false};

        McPatchWindow window = null;

        try {
            // 初始化控制台日志系统
            InitConsoleLogging(graphicsMode, enableLogFile);

            // 准备各种目录
            Path workDir = getWorkDirectory();
            Path progDir = getProgramDirectory(workDir);
            AppConfig config = new AppConfig(readConfig(progDir.resolve("mcpatch.yml")));
            Path baseDir = getUpdateDirectory(workDir, config);

            // 初始化文件日志系统
            String logFileName = graphicsMode ? "mcpatch.log" : "mcpatch.log.txt";
            Path logFilePath = progDir.resolve(logFileName);

            if (enableLogFile)
                 InitFileLogging(logFilePath);

            // 非独立进程启动时，使用标签标明日志所属模块
            if (startMethod == StartMethod.ModLoader || startMethod == StartMethod.JavaAgent)
                Log.openTag("Mcpatch");

            // 打印调试信息
            PrintEnvironmentInfo(graphicsMode, startMethod, baseDir, workDir);

            // 应用主题
            if (graphicsMode && !disableTheme && !config.disableTheme)
                SetupSwing.init();

            // 初始化UI
//            window = graphicsMode ? new McPatchWindow() : null;

            // 初始化窗口
            if (window != null) {
                window.setTitleText(config.windowTitle);
                window.setLabelText("正在连接到更新服务器");
                window.setLabelSecondaryText("");

                // 弹出窗口
                if (!config.silentMode)
                    window.show();
            }

            Work work = new Work();
            work.window = window;
            work.config = config;
            work.baseDir = baseDir;
            work.progDir = progDir;
            work.logFilePath = logFilePath;
            work.graphicsMode = graphicsMode;
            work.startMethod = startMethod;

            // 将更新任务单独放进单独线程执行，方便随时打断线程
            final Throwable[] ex = { null };

            Thread workThread = new Thread(() -> {
                try {
                    hasUpdate[0] = work.run();
                } catch (McpatchBusinessException e) {
                    ex[0] = e;
                }
            });

            workThread.setDaemon(true);
            workThread.setUncaughtExceptionHandler((ignored, e) -> ex[0] = e);

            // 点击窗口的叉时停止更新任务
            if (window != null) {
                window.onWindowClosing = w -> {
                    if (workThread.isAlive())
                        workThread.interrupt();
                };
            }

            // 启动更新任务
            workThread.start();
            try {
                workThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // 退出窗口
            if (window != null)
                window.destroy();

            // 处理工作线程里的异常
            Throwable ex1 = ex[0];

            if (ex1 != null) {
                boolean a = ex[0] instanceof InterruptedException;
                boolean b = ex[0] instanceof InterruptedIOException;
                boolean c = ex[0] instanceof ClosedByInterruptException;

                if (!a && !b && !c) {

                    // 打印异常日志
                    try {
                        Log.error(new McpatchBusinessException((Exception) ex1).toString());
                    } catch (Exception e) {
                        System.out.println("------------------------");
                        System.out.println(e);
                    }

                    // 图形模式下弹框显示错误
                    if (graphicsMode) {
                        boolean sp = startMethod == StartMethod.Standalone;

                        String className = ex1 instanceof McpatchBusinessException ? "" : ex1.getClass().getName() + "\n";
                        String errMsg = ex1.getMessage() != null ? ex1.getMessage() : "<No Exception Message>";
                        String errMessage = BytesUtils.stringBreak(className + errMsg, 80, "\n");
                        String title = "发生错误 " + Env.getVersion();
                        String content = errMessage + "\n";
                        content += !sp ? "点击\"是\"显示错误详情并停止启动Minecraft，" : "点击\"是\"显示错误详情并退出，";
                        content += !sp ? "点击\"否\"继续启动Minecraft" : "点击\"否\"直接退出程序";

                        boolean choice = DialogUtility.confirm(title, content);

                        if (!sp)
                        {
                            if (choice)
                            {
                                DialogUtility.error("错误详情 " + Env.getVersion(), ex1.toString());

                                throw ex1;
                            }
                        } else {
                            if (choice)
                                DialogUtility.error("错误详情 " + Env.getVersion(), ex1.toString());

                            throw ex1;
                        }
                    }
                } else {
                    Log.info("更新过程被用户打断！");
                }
            }
        } finally {
            if (window != null)
                window.destroy();

            Log.info("RAM: " + BytesUtils.convertBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

            // if (startMethod == StartMethod.Standalone)
            //     Runtime.getRuntime().exit(0);
        }

        return hasUpdate[0];
    }

    /**
     * 获取Jar文件所在的目录
     */
    static Path getProgramDirectory(Path workDir)
    {
        if (Env.isDevelopment())
            return workDir.resolve("test");

        return Env.getJarPath().getParent();
    }

    /**
     * 获取进程的工作目录
     */
    static Path getWorkDirectory() {
        Path userDir = Paths.get(System.getProperty("user.dir"));

        if (Env.isDevelopment()) {
            Path testDir = userDir.resolve("test");

            try {
                Files.createDirectories(testDir);
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
            return workDir.resolve("test");

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

    // 从外部/内部读取配置文件并将内容返回
    static Map<String, Object> readConfig(Path external) throws McpatchBusinessException {
        try {
//            System.out.println("aaa " + external.toFile().getAbsolutePath());

            Map<String, Object> result;

            Yaml yaml = new Yaml();

            // 如果外部配置文件存在，优先使用
            if (Files.exists(external)) {
                result = yaml.load(new String(Files.readAllBytes(external)));
            }

            // 如果内部配置文件存在，则读取内部的
            else {
                // 开发时必须要有外部配置文件
                if (Env.isDevelopment()) {
                    throw new McpatchBusinessException("找不到配置文件: mcpatch.yml，开发时必须要有配置文件");
                }

                // 读取内部配置文件
                try (JarFile jar = new JarFile(Env.getJarPath().toFile())) {
                    ZipEntry entry = jar.getJarEntry("mcpatch.yml");

                    try (InputStream stream = jar.getInputStream(entry)) {
                        result = yaml.load(stream);
                    }
                }
            }

//            System.out.println(result);

            return result;
//
//            if (content.startsWith(":")) {
//                try {
//                    content = new String(Base64.getDecoder().decode(content.substring(1)));
//                } catch (IllegalArgumentException e) {
//                    throw new InvalidConfigFileException();
//                }
//            }
        } catch (ParserException | IOException e) {
            throw new McpatchBusinessException(e);
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
        Log.info("可执行文件所在目录: " + (Env.isDevelopment() ? "Dev" : Env.getJarPath()));
        Log.info("应用版本: " + Env.getVersion() + " (" + Env.getGitCommit() + ")");
        Log.info("Java虚拟机: " + jvmVendor + " (" + jvmVersion + ")");
        Log.info("操纵系统: " + osName + "/" + osVersion + "/" + osArch);
    }
}
