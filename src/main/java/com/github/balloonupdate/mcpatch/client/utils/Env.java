package com.github.balloonupdate.mcpatch.client.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 环境信息
 */
public class Env {
    static Optional<Path> jarPathCache = null;

    static Map<String, String> manifestCache = null;

    /**
     * 获取版本号
     */
    public static String getVersion()
    {
        return getManifestValue("Version", "0.0.0");
    }

    /**
     * 获取git的commit hash
     */
    public static String getGitCommit() {
        return getManifestValue("Git-Commit", "no-commit-hash");
    }

    /**
     * 是否在开发中
     */
    public static boolean isDevelopment()
    {
        return getJarPath() == null;
    }

    /**
     * 获取Jar文件路径，开发环境下返回Null
     */
    public static Path getJarPath()
    {
        if (jarPathCache == (Optional<Path>) null) {
            boolean isPackaged = !Env.class.getResource("").getProtocol().equals("file");

            if (!isPackaged) {
                return null;
            }

            try {
                String url = URLDecoder.decode(Env.class.getProtectionDomain().getCodeSource().getLocation().getFile(), "UTF-8").replace("\\", "/");

                if (url.startsWith("/"))
                    url = url.substring(1);

                if (url.endsWith(".class") && url.contains("!")) {
                    String path = url.substring(0, url.lastIndexOf("!"));

                    if (path.contains("file:/")) {
                        path = path.substring(path.indexOf("file:/") + "file:/".length());
                    }

                    jarPathCache = Optional.of(Paths.get(path));
                } else {
                    jarPathCache = Optional.of(Paths.get(url));
                }
            } catch (UnsupportedEncodingException e) {
                jarPathCache = Optional.empty();
                return null;
            }
        }

        return jarPathCache.orElse(null);
    }

    /**
     * 读取版本信息
     */
    public static Map<String, String> Manifest() {
        if (manifestCache == null) {
            Map<String, String> manifest = new HashMap<>();

            try {
                for (Map.Entry<Object, Object> entry : getOriginManifest().entrySet()) {
                    manifest.put(entry.getKey().toString(), entry.getValue().toString());
                }
            } catch (IOException ignored) {}
            manifestCache = manifest;
        }

        return manifestCache;
    }

    /**
     * 获取一个 Manifest值
     */
    private static String getManifestValue(String key, String defaultValue) {
        return Manifest().getOrDefault(key, defaultValue);
    }

    /**
     * 获取原始的 Manifest
     */
    private static Attributes getOriginManifest() throws IOException {
        Path jarFile = getJarPath();

        if (jarFile == null) {
            throw new IOException("Manifest信息获取失败");
        }

        try (JarFile jar = new JarFile(jarFile.toFile())) {
            try (InputStream inputStream = jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF"))) {
                return new Manifest(inputStream).getMainAttributes();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read manifest", e);
        }
    }
}
