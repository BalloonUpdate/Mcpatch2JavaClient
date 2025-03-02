package com.github.balloonupdate.mcpatch.client.exceptions;

/**
 * 代表业务异常，出现这个异常时，下载会自动进行重试
 */
public class McpatchBusinessException extends Exception {
    public McpatchBusinessException(String message) {
        super(message);
    }

    public McpatchBusinessException(String message, Exception e) {
        super(message + "，原因：" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    public McpatchBusinessException(Exception e) {
        super("好像出现了错误\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        Throwable cause = getCause();

        sb.append(getMessage());
        sb.append("\n");

        if (cause != null) {
            sb.append(stackTraceToString(cause));
        } else {
            sb.append(stackTraceToString(this));
        }

        return sb.toString();
    }

    /**
     * 获取错误的调用堆栈并做成字符串返回
     */
    static String stackTraceToString(Throwable e) {
        StringBuilder sb = new StringBuilder();

        StackTraceElement[] frames = e.getStackTrace();

        for (int i = 0; i < frames.length; i++) {
//            sb.append("    ");
//            sb.append(i);
//            sb.append(": ");
            sb.append(frames[i].toString());
            sb.append("\n");
        }

        return sb.toString();
    }
}
