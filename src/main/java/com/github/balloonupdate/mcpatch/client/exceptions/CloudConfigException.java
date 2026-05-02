package com.github.balloonupdate.mcpatch.client.exceptions;

/**
 * 云端配置拉取过程中的统一异常类。
 * 所有云端配置相关的错误均通过该异常抛出，便于调用方统一捕获和处理。
 */
public class CloudConfigException extends Exception {
    public CloudConfigException(String message) {
        super(message);
    }

    public CloudConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
