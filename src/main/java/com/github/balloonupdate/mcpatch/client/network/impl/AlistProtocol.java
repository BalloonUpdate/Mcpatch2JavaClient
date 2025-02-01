package com.github.balloonupdate.mcpatch.client.network.impl;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.network.UpdatingServer;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import java.nio.file.Path;
import java.util.HashMap;

public class AlistProtocol implements UpdatingServer {
    /**
     * 本协议的编号，用来在出现网络错误时，区分是第几个url出现问题
     */
    int number;

    /**
     * 配置文件
     */
    AppConfig config;

    /**
     * 基本URL部分，会和文件名拼接起来成为完整的URL路径
     */
    String baseUrl;

    /**
     * HTTP 客户端
     */
    OkHttpClient client;

    /**
     * 原始路径缓存 path -> raw_url
     */
    HashMap<String, String> cache = new HashMap<>();

    public AlistProtocol(int number, String url, AppConfig config) {
        // 确保 URL 末尾有 `/`
        if (!url.endsWith("/")) {
            url = url + "/";
        }
    }

    @Override
    public String requestText(String path, Range range, String desc) throws McpatchBusinessException {
        return "";
    }

    @Override
    public void downloadFile(String path, Range range, String desc, Path writeTo, OnDownload callback) throws McpatchBusinessException {

    }

    @Override
    public void close() throws Exception {

    }

    /**
     * 发起一个通用请求
     * @param path 文件路径
     * @param range 字节范围
     * @param desc 请求的描述
     * @return 响应
     * @throws McpatchBusinessException 请求失败时
     */
    Response request(String path, Range range, String desc) throws McpatchBusinessException {
        String rawPath = cache.get(path);

        throw new RuntimeException("还没有实现这个方法");
    }
}
