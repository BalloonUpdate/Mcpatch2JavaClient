package com.github.balloonupdate.mcpatch.client.utils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件路径相关实用类
 */
public class PathUtility {
    /**
     * 获取文件名部分
     */
    public static String getFilename(String url) {
        String filename = url;

        if (url.contains("/"))
            filename = filename.substring(url.lastIndexOf("/") + 1);

        return filename;
    }

    /**
     * 遍历删除文件夹或者普通文件，如果文件不存在不会抛异常
     */
    public static void delete(Path path) throws IOException {
        if (!Files.exists(path))
            return;

        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    // 递归
                    delete(entry);
                }
            }
        }

        // 删除文件或空目录
        Files.delete(path);
    }
}
