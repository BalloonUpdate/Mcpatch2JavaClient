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
     * 日志的标签
     */
    public List<String> tags;

    /**
     * 日志后面是否要换行
     */
    public Boolean newLine;
}
