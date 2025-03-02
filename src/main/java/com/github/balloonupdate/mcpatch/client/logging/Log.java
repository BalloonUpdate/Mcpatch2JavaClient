package com.github.balloonupdate.mcpatch.client.logging;

import java.util.ArrayList;

/**
 * 日志记录器。负责打印日志和记录日志
 */
public class Log {
    /**
     * 所有注册的日志记录器
     */
    static ArrayList<LogHandler> handlers = new ArrayList<>();

    /**
     * 日志的 缩进文字 会显示在日志头的后面，日志文本的前面
     */
    static ArrayList<String> indents = new ArrayList<>();

    /**
     * 应用程序标识，当开启时，在每条日志的最前面应该增加 Mcpatch 的字样，用来区分这是 mcpatch 输出的日志
     */
    static boolean appIdentifierEnabled = false;

    /**
     * 记录一条 debug 日志
     */
    public static void debug(String message) {
        message(LogLevel.Debug, message);
    }

    /**
     * 记录一条 info 日志
     */
    public static void info(String message) {
        message(LogLevel.Info, message);
    }

    /**
     * 记录一条 warn 日志
     */
    public static void warn(String message) {
        message(LogLevel.Warn, message);
    }

    /**
     * 记录一条 error 日志
     */
    public static void error(String message) {
        message(LogLevel.Error, message);
    }

    /**
     * 记录一条日志
     * @param level 日志的等级
     * @param content 日志的内容
     */
    public static void message(LogLevel level, String content) {
        for (LogHandler handler : handlers) {
            if(level.ordinal() >= handler.getFilterLevel().ordinal()) {
                Message msg = new Message();

                msg.time = System.currentTimeMillis();
                msg.level = level;
                msg.content = content;
                msg.indents = indents;
                msg.appIdentifier = appIdentifierEnabled;

                handler.onMessage(msg);
            }
        }
    }

    /**
     * 开启一个 tag
     */
    public static void openIndent(String prefix) {
        indents.add(prefix);
    }

    /**
     * 关闭上一个 tag
     */
    public static void closeIndent() {
        if (!indents.isEmpty())
            indents.remove(indents.size() - 1);
    }

    /**
     * 设置应用程序标识是否打开
     */
    public static void setAppIdentifier(boolean enabled) {
        appIdentifierEnabled = enabled;
    }

    /**
     * 注册一个日志记录器
     */
    public static void addHandler(LogHandler handler) {
        handler.onStart();

        handlers.add(handler);
    }

    /**
     * 停止日志记录器
     */
    public static void stop() {
        for (LogHandler handler : handlers) {
            handler.onStart();
        }

        handlers.clear();
    }
}
