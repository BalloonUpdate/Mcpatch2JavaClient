package com.github.balloonupdate.mcpatch.client;

/**
 * modloader 支持类，用来支持从 modloader 启动 Mcpatch
 */
public class BalloonUpdateMain
{
    /**
     * 从ModLoader启动
     * @return 是否有文件更新，如果有返回true。其它情况返回false
     */
    public static boolean modloader(boolean enableLogFile, boolean disableTheme) throws Throwable {
        return Main.modloader(enableLogFile, disableTheme);
    }
}
