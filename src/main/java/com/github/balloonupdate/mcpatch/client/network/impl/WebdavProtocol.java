package com.github.balloonupdate.mcpatch.client.network.impl;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.network.UpdatingServer;
import com.github.balloonupdate.mcpatch.client.utils.BytesUtils;
import com.github.balloonupdate.mcpatch.client.utils.ReduceReportingFrequency;
import com.github.sardine.impl.SardineException;
import com.github.sardine.impl.SardineImpl;
import com.github.sardine.impl.handler.VoidResponseHandler;
import com.github.sardine.impl.io.ContentLengthInputStream;
import com.github.sardine.impl.io.HttpMethodReleaseInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代表 Webdav 协议的实现
 */
public class WebdavProtocol implements UpdatingServer {
    /**
     * 本协议的编号，用来在出现网络错误时，区分是第几个url出现问题
     */
    int number;

    /**
     * 配置文件
     */
    AppConfig config;

    /**
     * 客户端对象
     */
    McPatchSardineImpl client;

    /**
     * 协议名
     */
    String scheme;

    /**
     * 主机名
     */
    String host;

    /**
     * 端口
     */
    int port;

    /**
     * 用户名
     */
    String username;

    /**
     * 密码
     */
    String password;

    /**
     * 子路径
     */
    String root;

    public WebdavProtocol(int number, String url, AppConfig config) throws McpatchBusinessException {
        this.number = number;
        this.config = config;

        Pattern reg = Pattern.compile("^(webdavs?)://(.+?):(.+?):(.+?):(\\d+)((?:/[^/]+)*)$");

        Matcher matchResult = reg.matcher(url);

        if (!matchResult.find()) {
            throw new McpatchBusinessException("第 " + number + " 个url格式不正确，无法正确识别成 webdav 连接，请检查格式");
        }

        scheme = matchResult.group(1).equals("webdav") ? "http" : "https";
        username = matchResult.group(2);
        password = matchResult.group(3);
        host = matchResult.group(4);
        port = Integer.parseInt(matchResult.group(5));
        root = matchResult.groupCount() >= 6 ? matchResult.group(6) : "";

//        Log.info("scheme: $scheme");
//        Log.info("host: $host");
//        Log.info("port: $port");
//        Log.info("username: $username");
//        Log.info("password: $password");
//        Log.info("basepath: $basepath");

        client = new McPatchSardineImpl(config, username, password);
        client.enableCompression();
        client.enablePreemptiveAuthentication(host);

//        Log.debug("Current Directory: ${webdav.list(buildURI("")).joinToString { it.path }}")
    }

    @Override
    public String requestText(String path, Range range, String desc) throws McpatchBusinessException {
        String url = buildURL(path);

        GetAltResponse response = client.getAlt(url, range);

        try (ContentLengthInputStream remote = response.stream) {
            return BytesUtils.readIntoString(remote);
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }
    }

    @Override
    public void downloadFile(String path, Range range, String desc, Path writeTo, OnDownload callback) throws McpatchBusinessException {
        String url = buildURL(path);
//        Log.debug("webdav request on $url, write to: ${writeTo.path}")

        GetAltResponse response = client.getAlt(url, range);

        try (ContentLengthInputStream input = response.stream) {
            long contentLength = input.getLength();

            try (OutputStream output = Files.newOutputStream(writeTo)) {
                long bytesReceived = 0;
                byte[] buf = new byte[BytesUtils.chooseBufferSize(contentLength)];

                ReduceReportingFrequency report = new ReduceReportingFrequency();

                int len;

                while (true) {
                    len = input.read(buf);

                    if (len == -1) {
                        break;
                    }

                    output.write(buf, 0, len);
                    bytesReceived += len;

                    // 报告进度
                    long d = report.feed(len);

                    if (d > 0) {
                        callback.on(d, bytesReceived, contentLength);
                    }
                }

                // 完成下载
                callback.on(0, contentLength, contentLength);
            }
        } catch (IOException e) {
            throw new McpatchBusinessException(e);
        }
    }

    @Override
    public void close() throws Exception {

    }

    /**
     * 构建完整的 URL
     * @param path 文件路径
     */
    String buildURL(String path) {
        return String.format("%s://%s:%d%s/%s", scheme, host, port, root, path);
    }

    /**
     * 构建一个适合webdav的自定义参数的HttpClient对象
     */
    static HttpClientBuilder builder(AppConfig config) {
        HttpClientBuilder builder = HttpClientBuilder.create()
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(config.httpTimeout)
                        .build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setBufferSize(1024 * 1024)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(config.httpTimeout)
                        .build());

        // 忽略证书验证
        if (config.ignoreSSLCertificate)
            builder.setSSLContext(new HttpProtocol.IgnoreSSLCert().context);

        return builder;
    }

    /**
     * 魔改版Get请求的响应
     */
    public static class GetAltResponse {
        public ContentLengthInputStream stream;
        public HttpResponse response;

        public GetAltResponse(ContentLengthInputStream stream, HttpResponse response) {
            this.stream = stream;
            this.response = response;
        }
    }

    /**
     * 自定义的SardineImpl实现，用来插入自定义 headers
     */
    public static class McPatchSardineImpl extends SardineImpl {
        AppConfig config;

        public McPatchSardineImpl(AppConfig config, String username, String password) {
            super(WebdavProtocol.builder(config), username, password);

            this.config = config;
        }

        GetAltResponse getAlt(String url, Range range) throws McpatchBusinessException {
            try {
                HttpGet get = new HttpGet(url);

                // 只请求部分文件
                if (range.len() > 0) {
                    get.addHeader("Range", String.format("bytes=%d-%d", range.start, range.end - 1));
                }

                for (Map.Entry<String, String> p : config.httpHeaders.entrySet())
                    get.addHeader(p.getKey(), p.getValue());

                // Must use #execute without handler, otherwise the entity is consumed
                // already after the handler exits.
                HttpResponse response = this.execute(get);
                VoidResponseHandler handler = new VoidResponseHandler();

                try {
                    try {
                        handler.handleResponse(response);
                    } catch (SardineException e) {
                        ContentLengthInputStream stream = new ContentLengthInputStream(new HttpMethodReleaseInputStream(response), response.getEntity().getContentLength());
                        String body = BytesUtils.readIntoString(stream);

                        throw new McpatchBusinessException("Webdav 发生了错误\n" + body + "\n-----------\n" + e, e);
                    }

                    // 必须返回 content-length
                    if (response.getEntity().getContentLength() < 0)
                        throw new McpatchBusinessException("Webdav 响应中缺少 content-length 字段，或者字段返回的值不正确");

                    // Will abort the read when closed before EOF.
                    ContentLengthInputStream stream = new ContentLengthInputStream(new HttpMethodReleaseInputStream(response), response.getEntity().getContentLength());

                    return new GetAltResponse(stream, response);
                } catch (IOException ex) {
                    get.abort();
                    throw ex;
                }
            } catch (IOException e) {
                throw new McpatchBusinessException(e);
            } catch (McpatchBusinessException e) {
                throw e;
            }
        }
    }
}
