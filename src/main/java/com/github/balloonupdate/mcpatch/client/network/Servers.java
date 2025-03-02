package com.github.balloonupdate.mcpatch.client.network;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.Range;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.logging.Log;
import com.github.balloonupdate.mcpatch.client.network.impl.AlistProtocol;
import com.github.balloonupdate.mcpatch.client.network.impl.HttpProtocol;
import com.github.balloonupdate.mcpatch.client.network.impl.McpatchProtocol;
import com.github.balloonupdate.mcpatch.client.network.impl.WebdavProtocol;
import com.github.balloonupdate.mcpatch.client.utils.RuntimeAssert;

import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动重试和切换备用服务器的服务器类，各种业务逻辑都会直接和这个类打交道，而不是具体的协议实现类
 */
public class Servers implements UpdatingServer {
    /**
     * 配置文件
     */
    AppConfig config;

    /**
     * 服务器列表，遇到网络问题时会自动切换备用服务器
     */
    List<UpdatingServer> servers = new ArrayList<>();

    /**
     * 当前切换到第几个服务器了
     */
    int current = 0;

    public Servers(AppConfig config) throws McpatchBusinessException {
        this.config = config;

        // 解析配置文件
        for (int i = 0; i < config.urls.size(); i++) {
            String url = config.urls.get(i);

            if (url.startsWith("http")) {
                servers.add(new HttpProtocol(i, url, config));
            } else if (url.startsWith("mcpatch")) {
                servers.add(new McpatchProtocol(i, url, config));
            } else if (url.startsWith("webdav")) {
                servers.add(new WebdavProtocol(i, url, config));
            } else if (url.startsWith("alist")) {
                servers.add(new AlistProtocol(i, url, config));
            } else {
                throw new McpatchBusinessException("遇到无法识别的服务器链接：" + url);
            }
        }

        if (servers.isEmpty()) {
            throw new McpatchBusinessException("找不到任何更新服务器地址，请至少填写一个");
        }
    }

    @Override
    public String requestText(String path, Range range, String desc) throws McpatchBusinessException {
        return multipleAvailableServers(e -> e.requestText(path, range, desc));
    }

    @Override
    public void downloadFile(String path, Range range, String desc, Path writeTo, OnDownload callback, OnFail fallback) throws McpatchBusinessException {
        multipleAvailableServers(e -> {
            e.downloadFile(path, range, desc, writeTo, callback, fallback);

            // 没办法这里必须要返回一个东西，不然编译不通过
            return 114514;
        });
    }

    /**
     * 实现自动重试机制+自动切换服务器源，如果遇到网络失败会重试，重试也不行就会切换到下一个更新服务器
     *
     * @param task 要运行的任务
     * @return task 的返回值结果
     */
    private <T> T multipleAvailableServers(Retry<T, UpdatingServer> task) throws McpatchBusinessException
    {
        McpatchBusinessException ex = null;

        // 记录第几次出错
        int errorTimes = 0;

        // 每个服务器挨个试
        while (current < servers.size()) {
            UpdatingServer server = servers.get(current);

            int times = config.reties;

            while (--times >= 0) {
                try {
                    return task.runTask(server);
                } catch (McpatchBusinessException e) {
                    try {
                        server.close();
                    } catch (Exception exc) {
                        throw new McpatchBusinessException(exc);
                    }

                    // 用户打断了更新
                    if (e.getCause() instanceof ClosedByInterruptException) {
                        throw new McpatchBusinessException("用户打断了更新", (Exception) e.getCause());
                    }

                    // 记录一次错误
                    ex = e;

                    errorTimes += 1;
                    Log.openIndent("第" + errorTimes + "次出错：");

                    Log.warn("");
                    Log.warn(ex.toString());
                    Log.warn("retry " + times + "...");

                    Log.closeIndent();

                    // 不是最后一次机会的话就等待一下下
                    if (times > 0) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }

            // 如果还有后续服务器，就打印错误
            if (current < servers.size() - 1)
                Log.error(ex.toString());

            // 切换服务器
            current += 1;
        }

        RuntimeAssert.isTrue(ex != null);

        throw ex;
    }

    @Override
    public void close() throws Exception {
        for (UpdatingServer source : servers) {
            source.close();
        }
    }

    @FunctionalInterface
    public interface Retry<T, S> {
        T runTask(S server) throws McpatchBusinessException;
    }
}
