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
     * 日志的 tags 目前至少预留并没有使用
     */
    static ArrayList<String> tags = new ArrayList<>();

    /**
     * 记录一条 debug 日志
     */
    public static void debug(String message) {
        message(LogLevel.Debug, message, true);
    }

    /**
     * 记录一条 info 日志
     */
    public static void info(String message) {
        message(LogLevel.Info, message, true);
    }

    /**
     * 记录一条 warn 日志
     */
    public static void warn(String message) {
        message(LogLevel.Warn, message, true);
    }

    /**
     * 记录一条 error 日志
     */
    public static void error(String message) {
        message(LogLevel.Error, message, true);
    }

    /**
     * 记录一条日志
     * @param level 日志的等级
     * @param content 日志的内容
     * @param newLine 日志内容后面是否跟上一个换行符
     */
    public static void message(LogLevel level, String content, Boolean newLine) {
        for (LogHandler handler : handlers) {
            if(level.ordinal() >= handler.getFilterLevel().ordinal()) {
                Message msg = new Message();

                msg.time = System.currentTimeMillis();
                msg.level = level;
                msg.content = content;
                msg.tags = tags;
                msg.newLine = newLine;

                handler.onMessage(msg);
            }
        }
    }

    /**
     * 开启一个 tag
     */
    public static void openTag(String tag) {
        tags.add(tag);
    }

    /**
     * 开启上一个 tag
     */
    public static void closeTag() {
        if (!tags.isEmpty())
            tags.remove(tags.size() - 1);
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
