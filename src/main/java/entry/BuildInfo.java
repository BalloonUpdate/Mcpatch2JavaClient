package entry;

/**
 * 版本信息 — 由构建脚本注入版本号
 * ZKM会保留此类（作为keepClass的依赖），但字段名会被混淆
 */
public final class BuildInfo {
    public static final String VERSION = "4.0.0";
    public static final String GIT_COMMIT = "zkm-build";

    private BuildInfo() {}
}
