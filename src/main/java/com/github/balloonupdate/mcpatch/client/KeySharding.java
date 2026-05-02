package com.github.balloonupdate.mcpatch.client;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 密钥碎片化（Key Sharding）核心工具类。
 * <p>
 * 基于 XOR 运算的可逆特性，将密钥拆分为 3 个独立碎片，或从 3 个碎片还原密钥。
 * 任何单个碎片都不包含原始密钥的任何信息，只有三个碎片全部凑齐才能还原。
 * <p>
 * 分片算法：
 * <pre>
 * Fragment1 = Mask1（随机生成）
 * Fragment2 = Mask2（随机生成）
 * Fragment3 = K XOR Mask1 XOR Mask2
 * </pre>
 * 还原算法：{@code K = Fragment1 XOR Fragment2 XOR Fragment3}
 */
public class KeySharding {

    /**
     * 将密钥拆分为 3 个 XOR 碎片（仅用于离线初始化工具）。
     *
     * @param keyBytes 原始密钥字节数组（AES-128 为 16 字节）
     * @return 包含 3 个碎片的数组，每个碎片与原始密钥等长
     * @throws IllegalArgumentException 密钥为空或长度不合法时抛出
     */
    public static byte[][] splitKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("密钥不能为空");
        }

        int len = keyBytes.length;
        byte[] frag1 = new byte[len];
        byte[] frag2 = new byte[len];
        byte[] frag3 = new byte[len];

        new SecureRandom().nextBytes(frag1);
        new SecureRandom().nextBytes(frag2);

        for (int i = 0; i < len; i++) {
            frag3[i] = (byte) (keyBytes[i] ^ frag1[i] ^ frag2[i]);
        }

        return new byte[][]{frag1, frag2, frag3};
    }

    /**
     * 从 3 个碎片还原密钥。
     * <p>
     * 还原公式：original = frag1 XOR frag2 XOR frag3
     *
     * @param frag1 碎片1
     * @param frag2 碎片2
     * @param frag3 碎片3
     * @return 还原后的原始密钥字节数组
     * @throws IllegalArgumentException 碎片长度不一致时抛出
     */
    public static byte[] assembleKey(byte[] frag1, byte[] frag2, byte[] frag3) {
        if (frag1 == null || frag2 == null || frag3 == null) {
            throw new IllegalArgumentException("碎片不能为 null");
        }
        if (frag1.length != frag2.length || frag2.length != frag3.length) {
            throw new IllegalArgumentException(
                    "碎片长度不一致，无法还原密钥: frag1=" + frag1.length
                            + ", frag2=" + frag2.length + ", frag3=" + frag3.length);
        }

        byte[] key = new byte[frag1.length];
        for (int i = 0; i < frag1.length; i++) {
            key[i] = (byte) (frag1[i] ^ frag2[i] ^ frag3[i]);
        }
        return key;
    }
}
