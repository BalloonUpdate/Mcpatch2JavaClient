package com.github.balloonupdate.mcpatch.client;

import com.github.balloonupdate.mcpatch.client.logging.Log;

import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.Random;

/**
 * Fragment3 本地存储管理。
 * <p>
 * Fragment3 不在 JAR 包内预置，而是在客户端首次启动时动态计算生成，
 * 然后写入本地文件系统的隐藏位置。存储路径采用混淆设计，
 * 文件名使用随机十六进制前缀以避免被搜索到。
 * <p>
 * 存储位置：.minecraft/assets/indexes/v{randomHex}（看起来像 Minecraft 资源索引文件）
 * 文件内容：Base64 编码的 Fragment3 字节
 * <p>
 * 即使 JAR 包被完全逆向，攻击者也只能获得 Fragment1 和 Fragment2，
 * 缺少 Fragment3 仍然无法还原密钥。
 */
public class FragmentStore {

    /**
     * Fragment3 存储目录（相对于程序运行目录）
     * 伪装成 Minecraft 资源索引目录
     */
    private static final String FRAG_DIR =
            ".minecraft" + File.separator
                    + "assets" + File.separator + "indexes";

    /**
     * Fragment3 文件名前缀（看起来像 Minecraft 版本资源索引文件）
     */
    private static final String FRAG_PREFIX = "v";

    /**
     * 首次启动时计算 Fragment3 并写入文件。
     * <p>
     * Fragment3 = aesKey XOR frag1 XOR frag2
     * <p>
     * 此方法应由离线初始化工具调用，运行完成后可删除工具代码。
     *
     * @param frag1      Fragment1 字节数组（来自 BuildInfo）
     * @param frag2      Fragment2 字节数组（来自 ThemeConfig）
     * @param fullAesKey 完整的 AES 密钥字节数组
     * @throws IOException 文件写入失败时抛出
     */
    public static void initFragment3(
            byte[] frag1, byte[] frag2,
            byte[] fullAesKey) throws IOException {
        byte[] frag3 = new byte[fullAesKey.length];
        for (int i = 0; i < fullAesKey.length; i++) {
            frag3[i] = (byte) (fullAesKey[i] ^ frag1[i] ^ frag2[i]);
        }

        String fileName = FRAG_PREFIX
                + Integer.toHexString(new Random().nextInt(0xFFFF));
        Path path = Paths.get(FRAG_DIR, fileName);

        Files.createDirectories(path.getParent());

        String encoded = Base64.getEncoder().encodeToString(frag3);
        Files.writeString(path, encoded);

        Log.info("[FragmentStore] Fragment3 已写入: " + path);
    }

    /**
     * 运行时加载 Fragment3。
     * <p>
     * 在 Fragment3 存储目录中查找以 "v" 开头且长度小于 10 的文件，
     * 读取并 Base64 解码为字节数组。
     *
     * @return Fragment3 字节数组，文件不存在时返回 null
     * @throws IOException 文件读取失败时抛出
     */
    public static byte[] loadFragment3() throws IOException {
        File dir = new File(FRAG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            Log.warn("[FragmentStore] Fragment3 目录不存在: " + FRAG_DIR + "（首次启动时应自动初始化）");
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(FRAG_PREFIX) && name.length() < 10) {
                String encoded = Files.readString(f.toPath());
                byte[] frag3 = Base64.getDecoder().decode(encoded.trim());
                Log.debug("[FragmentStore] Fragment3 已加载: " + name);
                return frag3;
            }
        }

        Log.warn("[FragmentStore] 未找到 Fragment3 文件（首次启动时应自动初始化）");
        return null;
    }

    /**
     * 检查 Fragment3 是否已初始化。
     *
     * @return Fragment3 文件是否存在
     */
    public static boolean isFragment3Initialized() {
        File dir = new File(FRAG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(FRAG_PREFIX) && name.length() < 10) {
                return true;
            }
        }
        return false;
    }

    /**
     * 密钥轮换时删除旧的 Fragment3 文件。
     * <p>
     * 初始化新的 Fragment3 前，应先调用此方法清除旧碎片。
     *
     * @throws IOException 文件删除失败时抛出
     */
    public static void removeFragment3() throws IOException {
        File dir = new File(FRAG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(FRAG_PREFIX) && name.length() < 10) {
                Files.deleteIfExists(f.toPath());
                Log.info("[FragmentStore] 已删除旧 Fragment3: " + name);
            }
        }
    }

    private FragmentStore() {
        // 禁止实例化
    }
}
