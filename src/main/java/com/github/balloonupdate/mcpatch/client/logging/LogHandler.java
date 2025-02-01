package com.github.balloonupdate.mcpatch.client.logging;

/**
 * 代表一个抽象的日志记录器接口
 */
public interface LogHandler {
    /**
     * 获取当前日志记录器的记录等级，重要程度低于这个等级的日志不会被发送到这个日志记录器被处理
     */
    LogLevel getFilterLevel();

    /**
     * 日志记录器的启动事件，用来做一些初始化资源操作
     */
    void onStart();

    /**
     * 日志记录器的停止事件，用来做一些销毁资源操作
     */
    void onStop();

    /**
     * 处理日志事件。当有日志到来时，且重要程度大于等于日志记录器的等级时，就会调用此方法
     */
    void onMessage(Message message);
}
