package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.config.AppConfig;
import com.github.balloonupdate.mcpatch.client.data.*;
import com.github.balloonupdate.mcpatch.client.exceptions.McpatchBusinessException;
import com.github.balloonupdate.mcpatch.client.logging.Log;
import com.github.balloonupdate.mcpatch.client.network.Servers;
import com.github.balloonupdate.mcpatch.client.ui.ChangeLogs;
import com.github.balloonupdate.mcpatch.client.ui.McPatchWindow;
import com.github.balloonupdate.mcpatch.client.utils.*;
import org.json.JSONArray;
import org.json.JSONException;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 代表更新的主逻辑
 */
public class Work {
    /**
     * UI窗口，非图形模式下会是null
     */
    McPatchWindow window;

    /**
     * 配置文件
     */
    AppConfig config;

    /**
     * 基本更新目录，所有更新的文件都从这里开始计算起始路径
     */
    Path baseDir;

    /**
     * 可执行程序所在目录，如果是开发中则是 test 目录
     */
    Path progDir;

    /**
     * 日志文件路径
     */
    Path logFilePath;

    /**
     * 是否处于图形模式下
     */
    boolean graphicsMode;

    /**
     * 程序的启动方式
     */
    Main.StartMethod startMethod;

    /**
     * 逻辑入口
     */
    public boolean run() throws McpatchBusinessException {
        // 显示窗口
        if (window != null && !config.silentMode)
            window.show();

        try(Servers server = new Servers(config)) {
            return run2(server);
        } catch (McpatchBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new McpatchBusinessException(e);
        }
    }

    boolean run2(Servers server) throws IOException, McpatchBusinessException {
        // 读取当前版本号文件
        Path versionFile = progDir.resolve(config.versionFilePath);
        String currentVersion = !config.testMode && Files.exists(versionFile) ? new String(Files.readAllBytes(versionFile)) : "";

        Log.info("正在检查更新");

        if (window != null)
            window.setLabelText("正在检测更新");

        // 获取服务端最新版本号
        String indexJsonText = server.requestText("index.json", Range.Empty(), "index file");

        IndexFile serverVersions = IndexFile.loadFromJson(indexJsonText);

        // 检查服务端版本数量
        if (serverVersions.len() == 0) {
            throw new McpatchBusinessException("目前无法更新，因为服务端还没有打包任何更新包，需要先打包至少一个更新包");
        }

        // 输出服务端全部版本号
        Log.debug("server versions:");

        for (int i = 0; i < serverVersions.len(); i++) {
            Log.debug("  " + i + ". " + serverVersions.get(i).label);
        }

        // 检查版本是否有效
        if (!serverVersions.contains(currentVersion) && !currentVersion.isEmpty()) {
            throw new McpatchBusinessException("目前无法更新，因为客户端版本号 " + currentVersion + " 不在服务端版本号列表里，无法确定版本新旧关系");
        }

        // 不是最新版才更新
        String latestVersion = serverVersions.get(serverVersions.len() - 1).label;

        Log.debug("latest: " + latestVersion + ", current: " + currentVersion);

        boolean hasUpdate = !latestVersion.equals(currentVersion);

        if (hasUpdate) {
            // 静默模式需要推迟显示窗口
            if (window != null && config.silentMode) {
                window.show();
            }

            // 收集落后的版本
            List<VersionIndex> missingVersions = serverVersions.calculateMissingVersions(currentVersion);

            // 输出缺失的版本
            Log.debug("missing versions:");

            for (int i = 0; i < missingVersions.size(); i++) {
                Log.debug("  " + i + ". " + missingVersions.get(i).label);
            }

//            if (System.currentTimeMillis() > 1)
//                throw new McpatchBusinessException("" + missingVersions.size());

            // 下载所有更新包元数据
            ArrayList<TempVersionMeta> versionMetas = new ArrayList<>();
            int counter = 1;

            for (VersionIndex ver : missingVersions) {
                String tip = String.format("正在下载元数据 %s (%d/%d)", ver.label, counter, missingVersions.size());

                Log.debug(tip);

                if (window != null)
                    window.setLabelText(tip);

                counter += 1;

                // 下载元数据
                Range range = new Range(ver.offset, ver.offset + ver.len);
                String meta_text;

                try {
                    meta_text = server.requestText(ver.filename, range, "metadata of " + ver.label);
                } catch (McpatchBusinessException e) {
                    throw new McpatchBusinessException("元数据下载失败", e);
                }

                // 解码元数据
                JSONArray metas;

                try {
                    metas = new JSONArray(meta_text);
                } catch (JSONException e) {
                    throw new McpatchBusinessException("元数据解码失败", e);
                }

                // 避免重复收集元数据
                // 这是一个历史遗留问题，早期的管理端会重复生成元数据，导致更新包元数据达到数Gb的大小
                // 后来的版本已经修复了，有问题的更新包也可以通过合并更新包功能来修复
                // 但早期创建更新包仍然会有这个问题，特别是有很多用户从来没有用过合并功能的，所以在客户端增加一下容错
                for (int i = 0; i < metas.length(); i++) {
                    VersionMeta meta = new VersionMeta(metas.getJSONObject(i));

                    // 去重
                    if (!versionMetas.stream().anyMatch(e -> e.metadata.label.equals(meta.label))) {
                        versionMetas.add(new TempVersionMeta(ver.filename, meta));
                    }
                }
            }

            // 定位临时目录
            Path tempDir = baseDir.resolve(".mcpatch-temp");

            // 解析元数据，收集要更新的文件列表
            ArrayList<String> createFolders = new ArrayList<>();
            ArrayList<TempUpdateFile> updateFiles = new ArrayList<>();
            ArrayList<String> deleteFolders = new ArrayList<>();
            ArrayList<String> deleteFiles = new ArrayList<>();
            ArrayList<TempMoveFile> moveFiles = new ArrayList<>();

            Log.debug("正在收集要更新的文件");

            if (window != null)
                window.setLabelText("正在收集要更新的文件");

            for (TempVersionMeta meta : versionMetas) {
                for (FileChange change : meta.metadata.changes) {
                    if (change instanceof FileChange.CreateFolder) {
                        FileChange.CreateFolder op = (FileChange.CreateFolder) change;

                        RuntimeAssert.isTrue(!createFolders.contains(op.path));

                        // 先删除 deleteFolders 里的文件夹。没有的话，再加入 createFolders 里面
                        if (deleteFolders.stream().anyMatch(e -> e.equals(op.path))) {
                            deleteFolders.remove(op.path);
                        } else {
                            createFolders.add(op.path);
                        }
                    }

                    if (change instanceof FileChange.UpdateFile) {
                        FileChange.UpdateFile op = (FileChange.UpdateFile) change;

                        // 删除已有的东西，避免下面重复添加报错
                        updateFiles.removeIf(e -> e.path.equals(op.path));

                        // 将文件从删除列表里移除
                        deleteFiles.remove(op.path);

                        // 收集起来
                        Path tempPath = tempDir.resolve(op.path + ".temp");
                        updateFiles.add(new TempUpdateFile(meta.filename, meta.metadata.label, op, tempPath));
                    }

                    if (change instanceof FileChange.DeleteFolder) {
                        FileChange.DeleteFolder op = (FileChange.DeleteFolder) change;

                        // 先删除 createFolders 里的文件夹。没有的话，再加入 deleteFolders 里面
                        if (createFolders.contains(op.path)) {
                            createFolders.remove(op.path);
                        } else {
                            deleteFolders.add(op.path);
                        }
                    }

                    if (change instanceof FileChange.DeleteFile) {
                        FileChange.DeleteFile op = (FileChange.DeleteFile) change;

                        // 处理那些刚下载又马上要被删的文件，这些文件不用重复下载
                        if (updateFiles.stream().anyMatch(e -> e.path.equals(op.path))) {
                            updateFiles.removeIf(e -> e.path.equals(op.path));
                        }

                        deleteFiles.add(op.path);
                    }

                    if (change instanceof FileChange.MoveFile) {
                        FileChange.MoveFile op = (FileChange.MoveFile) change;

                        // 单独处理还没有下载的文件
                        Optional<TempUpdateFile> find = updateFiles.stream()
                                .filter(e -> e.path.equals(op.from))
                                .findFirst();

                        if (find.isPresent()) {
                            // 不能和别人的to冲突了
                            RuntimeAssert.isTrue(!moveFiles.stream().anyMatch(e -> e.to.equals(op.to)));

                            // 更新下载路径
                            find.get().path = op.to;

                        } else {
                            // 不能和别人的from或者to冲突了
                            RuntimeAssert.isTrue(!moveFiles.stream().anyMatch(e -> e.from.equals(op.from) || e.to.equals(op.to)));

                            moveFiles.add(new TempMoveFile(op.from, op.to));
                        }
                    }
                }
            }

            // 过滤一些不安全行为
            // 1.不能更新自己
            Path currentJar = Env.getJarPath();

            if (currentJar != null) {
                createFolders.removeIf(e -> baseDir.resolve(e).equals(currentJar));
                updateFiles.removeIf(e -> baseDir.resolve(e.path).equals(currentJar));
                deleteFiles.removeIf(e -> baseDir.resolve(e).equals(currentJar));
                moveFiles.removeIf(e -> baseDir.resolve(e.from).equals(currentJar) || baseDir.resolve(e.to).equals(currentJar));
            }

            // 2.不能更新日志文件
            createFolders.removeIf(e -> baseDir.resolve(e).equals(logFilePath));
            updateFiles.removeIf(e -> baseDir.resolve(e.path).equals(logFilePath));
            deleteFiles.removeIf(e -> baseDir.resolve(e).equals(logFilePath));
            moveFiles.removeIf(e -> baseDir.resolve(e.from).equals(logFilePath) || baseDir.resolve(e.to).equals(logFilePath));

            // 单独为移动文件输出一份日志，具体原因忘了，但是输出一下好像也没有坏处
            for (TempMoveFile mv : moveFiles) {
                Log.debug(String.format("move files: {} => %s", mv.from, mv.to));
            }

            // 执行更新流程
            // 1.处理要下载的文件，下载到临时文件
            // 创建临时文件夹
            if (!updateFiles.isEmpty()) {
                try {
                    Files.createDirectories(tempDir);
                } catch (Exception e) {
                    throw new McpatchBusinessException("创建临时目录失败", e);
                }
            }

            // 尽可能跳过要下载的文件
            for (int i = updateFiles.size() - 1; i >= 0; i--) {
                if (config.testMode)
                    break;

                TempUpdateFile f = updateFiles.get(i);

                Path targetPath = baseDir.resolve(f.path);

                // 检查一下看能不能跳过下载
                // 1.如果不存在的话，肯定跳过不了
                if (!Files.exists(targetPath)) {
                    continue;
                }

                // 获取元数据，准备进一步判断
                FileTime mtime;

                try {
                    mtime = Files.getLastModifiedTime(targetPath);
                } catch (IOException ex) {
                    throw new McpatchBusinessException("获取文件修改时间失败 " + targetPath, ex);
                }

                // 2.判断文件时间
                if ((mtime.toMillis() / 1000 - f.modified) > 5) {
                    continue;
                }

                // 3.对比hash
                String hash;

                try {
                    hash = HashUtility.calculateHash(targetPath);
                } catch (IOException ex) {
                    throw new McpatchBusinessException("计算文件hash时遇到问题", ex);
                }

                if (!hash.equals(f.hash)) {
                    continue;
                }

                // 执行到这里的就是可以跳过的了
                updateFiles.remove(i);
            }

            // 尽可能跳过要创建的目录
            for (int i = createFolders.size() - 1; i >= 0; i--) {
                String f = createFolders.get(i);
                Path path = baseDir.resolve(f);

                if (Files.exists(path))
                    createFolders.remove(i);
            }

            // 尽可能跳过要删除的文件
            for (int i = deleteFiles.size() - 1; i >= 0; i--) {
                String f = deleteFiles.get(i);
                Path path = baseDir.resolve(f);

                if (!Files.exists(path))
                    deleteFiles.remove(i);
            }

            // 尽可能跳过要删除的目录
            for (int i = deleteFolders.size() - 1; i >= 0; i--) {
                String f = deleteFolders.get(i);
                Path path = baseDir.resolve(f);

                if (!Files.exists(path))
                    deleteFolders.remove(i);
            }


            // 准备开始下载更新数据
            Log.info("开始下载更新数据");

            if (window != null)
                window.setLabelText("下载更新数据");

            AtomicLong totalDownloaded = new AtomicLong();

            long totalBytes = updateFiles.stream().mapToLong(updateFile -> updateFile.length).sum();

            SpeedStat speed = new SpeedStat(1500);
            AtomicLong uiTimer = new AtomicLong(System.currentTimeMillis() - 600);

            // 1.下载到临时文件
            if (window != null) {
                window.setLabelText("准备开始下载文件");
            }

            for (TempUpdateFile f : updateFiles) {
                String filename = PathUtility.getFilename(f.path);

                Log.debug("  a.开始下载 " + f.tempPath);

                Path tempDirectory = f.tempPath.getParent();
                Files.createDirectories(tempDirectory);

                McpatchBusinessException ex = null;

                window.setProgressBarValue(500);

                for (int i = 0; i < config.reties + 1; i++) {
                    // 空文件不需要下载
                    if (f.length == 0) {
                        Files.createFile(f.tempPath);
                        break;
                    }

                    // 展示即将要开始下载内容
                    if (window != null) {
                        window.setProgressBarValue((int) (totalDownloaded.get() / (float) totalBytes * 1000));
                        window.setLabelSecondaryText(filename);
                        window.setLabelText(String.format("正在下载 %s 版本", f.label));
                    }

                    AtomicLong bytesCounter = new AtomicLong();

                    try {
                        Range range = new Range(f.offset, f.offset + f.length);
                        String desc = f.path + " in " + f.label;

                        server.downloadFile(f.containerName, range, desc, f.tempPath, (packageLength, bytesReceived, lengthExpected) -> {
                            // 计数
                            bytesCounter.addAndGet(packageLength);
                            totalDownloaded.addAndGet(packageLength);
                            speed.feed(packageLength);

                            // 更新UI
                            if (window != null) {
                                long now2 = System.currentTimeMillis();

                                if (now2 - uiTimer.get() > 300) {
                                    uiTimer.set(now2);

                                    window.setProgressBarText(String.format("%s/%s  -  %s/s", BytesUtils.convertBytes(totalDownloaded.get()), BytesUtils.convertBytes(totalBytes), speed.sampleSpeed2()));
                                    window.setProgressBarValue((int) (totalDownloaded.get() / (float) totalBytes * 1000));
                                }
                            }
                        });

                        // 修复文件 mtime
                        Files.setLastModifiedTime(f.tempPath, FileTime.from(f.modified, TimeUnit.SECONDS));

                        break;
                    } catch (McpatchBusinessException e) {
                        ex = e;

                        // 进度回退
                        totalDownloaded.addAndGet(-bytesCounter.get());

                        window.setProgressBarText(String.format("%s/%s  -  %s/s", BytesUtils.convertBytes(totalDownloaded.get()), BytesUtils.convertBytes(totalBytes), speed.sampleSpeed2()));
                        window.setProgressBarValue((int) (totalDownloaded.get() / (float) totalBytes * 1000));

                        if (i != config.reties) {
                            Log.error("retrying");
                        }
                    }
                }

                if (ex != null)
                    throw ex;

                // 校验文件
                String hash = HashUtility.calculateHash(f.tempPath);

                if (!hash.equals(f.hash))
                    throw new McpatchBusinessException(String.format("临时文件校验失败，预期 %s，实际 %s，文件路径 %s", f.hash, hash, f.tempPath.toFile().getAbsolutePath()));
            }

            if (window != null)
                window.setProgressBarValue(1000);

            // 2.创建目录
            Log.info("准备开始创建目录");

            if (window != null) {
                window.setLabelText("准备开始创建目录");
            }

            for (String f : createFolders) {
                Log.debug("  b.创建目录 " + f);

                Path path = baseDir.resolve(f);

                Files.createDirectories(path);
            }

            // 3.处理文件移动
            Log.info("正在移动文件，请不要关闭程序");

            if (window != null) {
                window.setLabelText("正在移动文件，请不要关闭程序");
            }

            for (TempMoveFile move : moveFiles) {
                Log.debug("  c.移动文件 " + move.from + " => " + move.to);

                Path from = baseDir.resolve(move.from);
                Path to = baseDir.resolve(move.to);

                if (Files.exists(from)) {
                    Files.move(from, to);
                }
            }

            // 4.处理文件删除
            Log.info("正在清理旧文件，请不要关闭程序");

            if (window != null) {
                window.setLabelText("正在清理旧文件，请不要关闭程序");
            }

            for (String f : deleteFiles) {
                Log.debug("  d.删除旧文件 " + f);

                Path path = baseDir.resolve(f);

                PathUtility.delete(path);
            }

            // 5.移回临时文件
            Log.info("正在移动临时文件，请不要关闭程序");

            if (window != null) {
                window.setLabelText("正在移动临时文件，请不要关闭程序");
            }

            for (TempUpdateFile f : updateFiles) {
                Path from = f.tempPath;
                Path to = baseDir.resolve(f.path);

                Log.debug(String.format("  e.移动临时文件 %s.temp => %s", f.path, f.path));

                PathUtility.delete(to);

                Files.move(from, to);
            }

            // 6.清理临时文件夹
            Log.info("正在清理临时文件夹");

            PathUtility.delete(tempDir);

            // 文件基本上更新完了，到这里就要进行收尾工作了
            Log.info("正在进行收尾工作");

            if (window != null) {
                window.setLabelText("正在进行收尾工作");
            }

            // 1.更新客户端版本号
            Files.write(versionFile, latestVersion.getBytes(StandardCharsets.UTF_8));

            // 2.生成更新记录
            String changelogs = "";

            for (TempVersionMeta meta : versionMetas) {
                changelogs += String.format("++++++++++ %s ++++++++++\n%s\n\n", meta.metadata.label, meta.metadata.logs);
            }

            Log.info("更新成功: \n" + changelogs.trim());

            // 3.弹出更新记录窗口
            if (window != null) {
                String content = String.format("已经从 %s 更新到 %s\r\n\r\n%s", currentVersion, latestVersion, changelogs.trim().replace("\n", "\r\n"));

                if (config.showFinishMessage) {
                    ChangeLogs cl = new ChangeLogs();

                    cl.setTitleText(config.windowTitle);
                    cl.setContentText(content);

                    if (config.autoCloseChangelogs > 0)
                        cl.setAutoClose(config.autoCloseChangelogs);

                    cl.waitForClose();
                }
            }

            // 4.输出一些更新信息
            Log.info("成功更新以下版本：\n" + missingVersions.stream().map(e -> e.label).collect(Collectors.joining("\n")));

        } else {
            // 提示没有更新
            Log.info("暂时木有更新");

            if (window != null) {
                window.setLabelText("暂时没有更新");

                if (config.showFinishMessage) {
                    String title = config.windowTitle;
                    String content = "暂时没有更新，当前版本：" + currentVersion;

                    JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }

        if (startMethod != Main.StartMethod.Standalone)
            Log.info("continue to start Minecraft!");

        return hasUpdate;
    }
}
