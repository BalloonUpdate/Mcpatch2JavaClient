package com.github.balloonupdate.mcpatch.client.logging;

import java.util.List;

/**
 * 单条日志对象
 */
public class Message {
    /**
     * 日志的记录时间
     */
    public long time;

    /**
     * 日志的等级
     */
    public LogLevel level;

    /**
     * 日志的内容
     */
    public String content;

    /**
     * 缩进文字，支持多层
     */
    public List<String> indents;

    /**
     * 应用程序标识
     * @see "Log.appIdentifierEnabled"
     */
    public boolean appIdentifier;
}
