package com.github.balloonupdate.mcpatch.client.utils;

/**
 * 运行时的断言工具类
 */
public class RuntimeAssert {
    public static void isTrue(boolean condition) {
        if (!condition)
            throw new RuntimeException("assert failed");
    }
}
