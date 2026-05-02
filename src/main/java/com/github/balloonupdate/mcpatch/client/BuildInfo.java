package com.github.balloonupdate.mcpatch.client;

/**
 * 构建信息类（伪装存储 AES 密钥 Fragment1）。
 * <p>
 * BUILD_SIGNATURE 看似用于构建产物完整性校验的签名值，
 * 实际上是 AES-256 密钥的第一个 XOR 碎片（32 字节 = 64 位十六进制）。
 * <p>
 * 经过 R8/ProGuard 混淆后，类名和字段名都会被缩短为无意义的标识符，
 * 进一步增加逆向分析难度。
 * <p>
 * 部署时需替换为管理面板生成的实际碎片值。
 */
public class BuildInfo {

    /**
     * 看似构建签名的十六进制字符串，实际是 AES-256 密钥 Fragment1
     * <p>
     * 部署时替换为实际的 Fragment1 值（64 位 hex = 32 字节，适配 AES-256）
     */
    public static final String BUILD_SIGNATURE =
            "a3f7c2e8b1d49560a3f7c2e8b1d49560a3f7c2e8b1d49560a3f7c2e8b1d49560";

    /**
     * 构建版本号（伪装用）
     */
    public static final String BUILD_VERSION = "2.1.0";

    /**
     * 构建编号（伪装用）
     */
    public static final int BUILD_NUMBER = 2147483647;

    private BuildInfo() {
        // 禁止实例化
    }
}
