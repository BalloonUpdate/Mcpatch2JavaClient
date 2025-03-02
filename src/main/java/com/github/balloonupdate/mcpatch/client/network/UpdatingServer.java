package com.github.balloonupdate.mcpatch.client.network;

import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;

import java.nio.file.Path;

/**
 * 代表一个更新服务器的接口
 */
public interface UpdatingServer extends AutoCloseable {
    /**
     * 发起一个文件请求，并将响应解码成字符串
     *
     * @param path 文件的路径
     * @param range 文件的请求范围，不指定范围时可以传0..0，但不可以传null
     * @return 文本内容
     */
    String requestText(String path, Range range, String desc) throws McpatchBusinessException;

    /**
     * 发起一个二进制文件的下载请求，并通过回调函数报告下载进度
     *
     * @param path 文件的相对路径
     * @param writeTo 文件落盘位置
     * @param callback 报告下载进度的回调
     * @param fallback 下载失败的事件，通常会进行重试
     */
    void downloadFile(String path, Range range, String desc, Path writeTo, OnDownload callback, OnFail fallback) throws McpatchBusinessException;

//    /**
//     * 给一个文字打码，避免泄露账号密码登信息。通常用在日志中。目前此功能仅是预留，没有实装
//     *
//     * @param text 要打码的文字
//     * @return 打码后的文字
//     */
//    String shadowText(String text);

    /**
     * 下载文件的进度事件
     */
    @FunctionalInterface
    interface OnDownload {
        /**
         * 当文件下载时，会通过这个方法报告下载进度
         * @param batch 这次传输了多少字节
         * @param downloaded 本次文件下载共传输了多少字节
         * @param total 本次文件下载一共需要传输w多少字节
         */
        void on(long batch, long downloaded, long total);
    }

    /**
     * 下载文件失败的事件，提示UI回退进度条
     */
    @FunctionalInterface
    interface OnFail {
        /**
         * 当文件下载时，会通过这个方法告知下载失败了，提醒UI回退进度条
         * @param fallback 要回退多少字节
         */
        void on(long fallback);
    }
}
