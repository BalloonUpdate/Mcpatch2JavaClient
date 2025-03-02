package com.github.balloonupdate.mcpatch.client.network.impl;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.network.UpdatingServer;
import com.github.balloonupdate.mcpatch.client.utils.BytesUtils;
import com.github.balloonupdate.mcpatch.client.utils.ReduceReportingFrequency;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 代表 Mcpatch 私有协议的实现
 */
public class McpatchProtocol implements UpdatingServer {
    /**
     * 本协议的编号，用来在出现网络错误时，区分是第几个url出现问题
     */
    int number;

    /**
     * 配置文件
     */
    AppConfig config;

    /**
     * 远程主机地址
     */
    String host;

    /**
     * 远程主机端口
     */
    int port;

    /**
     * Socket 对象
     */
    Socket socket;

    /**
     * Socket 对象上的输入流
     */
    InputStream input;

    /**
     * Socket 对象上的输出流
     */
    OutputStream output;

    public McpatchProtocol(int number, String url, AppConfig config) throws McpatchBusinessException {
        this.number = number;
        this.config = config;

        String stripped = url.substring("mcpatch://".length());

        int index = stripped.lastIndexOf(":");

        if (index == -1)
            throw new McpatchBusinessException("私有协议链接格式不正确，端口不可省略：" + url);

        host = stripped.substring(0, index);
        port = Integer.parseInt(stripped.substring(index + 1));
    }

    @Override
    public String requestText(String path, Range range, String desc) throws McpatchBusinessException {
        long len = request(path, range, desc);

        // 处理边界情况
        if (len == 0)
            return "";

        byte[] buf = new byte[(int) len];

        try {
            BytesUtils.readAll(buf, input);
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }

        return new String(buf, StandardCharsets.UTF_8);
    }

    @Override
    public void downloadFile(String path, Range range, String desc, Path writeTo, OnDownload callback, OnFail fallback) throws McpatchBusinessException {
        long size = request(path, range, desc);

        // 本次文件传输一共累计传输了多少字节
        long downloaded = 0;

        try {
            try (OutputStream output = Files.newOutputStream(writeTo)) {
                byte[] buffer = new byte[BytesUtils.chooseBufferSize(size)];

                ReduceReportingFrequency report = new ReduceReportingFrequency();

                long remains = size;
                int len;

                do {
                    len = input.read(buffer, 0, (int) Math.min(buffer.length, remains));

                    remains -= len;

                    output.write(buffer, 0, len);

                    downloaded += len;

                    // 报告进度
                    long d = report.feed(len);

                    if (d > 0) {
                        callback.on(d, downloaded, size);
                    }

                } while (remains > 0);

                // 完成下载
                callback.on(0, size, size);
            }
        } catch (IOException e) {
            if (fallback != null)
                fallback.on(downloaded);

            throw new McpatchBusinessException(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (socket != null)
            socket.close();

        socket = null;
    }

    /**
     * 延迟建立连接
     */
    void lazyConnect() throws McpatchBusinessException {
        if (socket != null)
            return;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port));
            socket.setSoTimeout(config.privateTimeout);

            input = socket.getInputStream();
            output = socket.getOutputStream();
        } catch (IOException e) {
            socket = null;
            throw new McpatchBusinessException("私有协议建立连接失败", e);
        }
    }

    // 发送一个数据帧
    void sendDataFrame(byte[] data) throws McpatchBusinessException {
//        String a = BytesUtils.bytesToString(BytesUtils.longToBytesLE(data.length));
//        String b = BytesUtils.bytesToString(data);
//
//        Log.debug(String.format("len: %s, data: %s", a, b));

        try {
            // 先发送8字节的长度信息
            output.write(BytesUtils.longToBytesLE(data.length));

            // 然后发送实际的数据
            output.write(data);
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }
    }

    /**
     * 接受一个 long
     */
    long receiveLong() throws McpatchBusinessException {
        byte[] data = new byte[8];

        try {
            BytesUtils.readAll(data, input);
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }

        return BytesUtils.bytesLeToLong(data);
    }

    /**
     * 通用的发起请求
     * @param path 请求的文件名
     * @param range 请求的字节范围
     * @param desc 动作的描述
     * @return 状态码兼文件大小
     */
    long request(String path, Range range, String desc) throws McpatchBusinessException {
        // 建立连接
        lazyConnect();

        // 首先发送文件路径
        sendDataFrame(path.getBytes(StandardCharsets.UTF_8));

        // 然后发送下载范围
        try {
            output.write(BytesUtils.longToBytesLE(range.start));
            output.write(BytesUtils.longToBytesLE(range.end));
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }

        // 接收状态码或者文件大小（64位有符号整数）
        long len = receiveLong();

        if (len < 0) {
            String reason;

            if (len == -1)
                reason = "代表文件找不到";
            else if (len == -2)
                reason = "代表请求的文件字节范围不正确";
            else
                reason = "原因未知";

            String text = String.format("私有协议(%d)接收到了一个不正确的状态码: %d (%s) %s", number, len, reason, desc);

            throw new McpatchBusinessException(text);
        }

        return len;
    }
}
