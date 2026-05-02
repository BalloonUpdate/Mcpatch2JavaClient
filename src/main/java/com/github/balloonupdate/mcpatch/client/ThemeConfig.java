package com.github.balloonupdate.mcpatch.client;

/**
 * 主题配置类（伪装存储 AES 密钥 Fragment2）。
 * <p>
 * THEME_ACCENT_COLORS 看似界面主题色值数组，
 * 实际上每个值对应 Fragment2 的一个字节。
 * <p>
 * 使用 int 数组而非十六进制字符串，是因为十六进制字符串
 * 容易被 grep 或 strings 工具搜索到，而 int 数组的值在
 * 静态分析中看起来就像是普通的配置参数。
 * <p>
 * 部署时需替换为管理面板生成的实际碎片值。
 * AES-256 需要 32 字节密钥，因此 Fragment2 也为 32 字节。
 */
public class ThemeConfig {

    /**
     * 伪装成主题色值数组，每个值对应 Fragment2 的一个字节
     * <p>
     * 部署时替换为实际的 Fragment2 值（32 个字节，每个 0-255，适配 AES-256）
     */
    private static final int[] THEME_ACCENT_COLORS = {
            0xB4, 0xE2, 0x9A, 0x56, 0xD7, 0xF1, 0x83, 0x0C,
            0xB4, 0xE2, 0x9A, 0x56, 0xD7, 0xF1, 0x83, 0x0C,
            0x5A, 0x1F, 0xC8, 0x3D, 0xE7, 0x92, 0x0B, 0x46,
            0x5A, 0x1F, 0xC8, 0x3D, 0xE7, 0x92, 0x0B, 0x46
    };

    /**
     * 获取主题种子值（实际返回 Fragment2 的字节数组）。
     * <p>
     * 方法名具有误导性，看似与主题配置相关，
     * 实际返回的是 AES-256 密钥的第二个 XOR 碎片。
     *
     * @return Fragment2 字节数组（32 字节，适配 AES-256）
     */
    public static byte[] getThemeSeed() {
        byte[] result = new byte[THEME_ACCENT_COLORS.length];
        for (int i = 0; i < THEME_ACCENT_COLORS.length; i++) {
            result[i] = (byte) THEME_ACCENT_COLORS[i];
        }
        return result;
    }

    private ThemeConfig() {
        // 禁止实例化
    }
}
