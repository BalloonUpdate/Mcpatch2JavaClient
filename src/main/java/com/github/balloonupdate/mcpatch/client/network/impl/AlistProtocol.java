package com.github.balloonupdate.mcpatch.client.network.impl;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.logging.Log;
import com.github.balloonupdate.mcpatch.client.network.UpdatingServer;
import com.github.balloonupdate.mcpatch.client.utils.RuntimeAssert;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
     * 下载链接缓存 path -> raw_url
     */
    HashMap<String, String> cache = new HashMap<>();

    public AlistProtocol(int number, String url, AppConfig config) {
        // 确保 URL 末尾有 `/`
        if (!url.endsWith("/")) {
            url = url + "/";
        }

        // 去掉开头的 alist:// ，留下后面的部分
        url = url.substring("alist://".length());

        baseUrl = url;

        // 创建 HTTP 客户端对象
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 忽略证书
        if (config.ignoreSSLCertificate) {
            HttpProtocol.IgnoreSSLCert ignore = new HttpProtocol.IgnoreSSLCert();

            builder.sslSocketFactory(ignore.context.getSocketFactory(), ignore.trustManager);
        }

        client = builder
                .connectTimeout(config.httpTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(config.httpTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(config.httpTimeout, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String requestText(String path, Range range, String desc) throws McpatchBusinessException {
        String rawPath = cache.get(path);

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
        // 检查输入参数，start不能大于end
        boolean partial_file = range.start > 0 || range.end > 0;

        if (partial_file) {
            RuntimeAssert.isTrue(range.end >= range.start);
        }

        // 拼接 URL
        String url = baseUrl + path;

        // 构建请求
        Request req = buildRequest(url, range, null, null);

        try {
            Response rsp = client.newCall(req).execute();
            int code = rsp.code();

            // 检查状态码
            if ((!partial_file && (code < 200 || code >= 300)) || (partial_file && code != 206)) {
                // 如果状态码不对，就考虑输出响应体内容，因为通常会包含一些服务端返回的错误信息，对排查问题很有帮助
                String body = rsp.peekBody(300).string();

                String content = String.format("服务器(%d)返回了 %d 而不是206: %s (%s)\n%s", number, code, path, desc, body);

                throw new McpatchBusinessException(content);
            }

            // 检查content-length
            long len = rsp.body().contentLength();

            if (len == -1) {
                throw new McpatchBusinessException(String.format("服务器(%d)没有返回 content-length 头：%s (%s)", number, path, desc));
            }

            if (range.len() > 0 && len != range.len()) {
                String text = String.format("服务器(%d)返回的 content-length 头 %d 不等于 %d: %s", number, len, range.len(), path);

                throw new McpatchBusinessException(text);
            }

            return rsp;
        } catch (ConnectException e) {
            throw new McpatchBusinessException("连接被拒绝，请检查网络。" + url, e);
        } catch (SocketException e) {
            throw new McpatchBusinessException("连接中断，请检查网络。" + url, e);
        } catch (SocketTimeoutException e) {
            throw new McpatchBusinessException("连接超市，请检查网络。" + url, e);
        } catch (Exception e) {
            throw new McpatchBusinessException(e);
        }
    }

    /**
     * 构建一个请求
     * @param url 请求的 url
     * @param range 请求的范围
     * @param headers 额外的 headers
     * @return 响应
     */
    private Request buildRequest(String url, Range range, RequestBody body, Map<String, String> headers) {
        Request.Builder req = new Request.Builder().url(url);

        // 添加json响应请求
        req.addHeader("Content-Type", "application/json");

        // 只请求部分文件
        if (range.len() > 0) {
            req.addHeader("Range", String.format("bytes=%d-%d", range.start, range.end - 1));
        }

        // 添加额外headers
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet())
                req.addHeader(e.getKey(), e.getValue());
        }

        // 添加自定义headers
        for (Map.Entry<String, String> e : config.httpHeaders.entrySet()) {
            req.addHeader(e.getKey(), e.getValue());
        }

        // 添加body
        if (body != null) {
            req.setBody$okhttp(body);
        }

        return req.build();
    }

    /**
     * 获取文件的原始链接
     */
    String fetchDownloadLink(String filename) throws IOException, McpatchBusinessException {
        if (cache.containsKey(filename))
            return cache.get(filename);

        String path = baseUrl + filename;

        int split = path.indexOf("/", "https://".length());

        path = path.substring(split);

        Log.info("split: " + path);


        String url = baseUrl + "api/fs/get";

        String bodyText = String.format("\"path\": \"%s\",\"password\": \"\"", path);
        RequestBody body = RequestBody.create(bodyText, MediaType.get("text/json"));

        Request req = buildRequest(url, Range.Empty(), body, null);

        Response rsp = client.newCall(req).execute();

        if (!rsp.isSuccessful()) {
            // 如果状态码不对，就考虑输出响应体内容，因为通常会包含一些服务端返回的错误信息，对排查问题很有帮助
            String b = rsp.peekBody(300).string();

            String content = String.format("服务器(%d)返回了 %d 而不是206: %s (%s)\n%s", number, rsp.code(), path, "请求原始下载链接", b);

            throw new McpatchBusinessException(content);
        }

        JSONObject json = new JSONObject(rsp.body());

        String rawUrl = (String) json.query("/data/raw_url");

        cache.put(path, rawUrl);

        return rawUrl;
    }
}
