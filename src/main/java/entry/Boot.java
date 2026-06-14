package entry;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent Entry — Trampoline Architecture v2
 *
 * premain/main 是薄跳板，仅1行委托到ZKM会完全混淆的私有方法。
 * ZKM仅保留 premain/main 方法签名，其余所有方法被高强度混淆。
 *
 * 防护层：
 * - L8 ASM AES-256-CBC 加密所有字符串（含目标类名）
 * - StringDecryptor 调用栈验证阻止外部调用
 * - SecurityGuard 反调试+周期性自检
 */
public class Boot {

    /**
     * Java Agent premain — 跳板入口
     * ZKM保留签名: public static void premain(String, Instrumentation)
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Throwable {
        _p(agentArgs, inst);
    }

    /**
     * 独立运行入口 — 跳板
     * ZKM保留签名: public static void main(String[])
     */
    public static void main(String[] args) throws Throwable {
        _m(args);
    }

    // =================================================================
    // 以下所有方法将被ZKM高强度混淆（控制流+重命名+异常混淆+整数加密）
    // =================================================================

    /** premain 内部实现 */
    private static void _p(String a, Instrumentation i) throws Throwable {
        SecurityGuard.start();
        String cn = _d();
        Class<?> c = Class.forName(cn);
        java.lang.reflect.Method m = c.getMethod("premain", String.class, Instrumentation.class);
        m.invoke(null, a, i);
    }

    /** main 内部实现 */
    private static void _m(String[] a) throws Throwable {
        SecurityGuard.start();
        String cn = _d();
        Class<?> c = Class.forName(cn);
        java.lang.reflect.Method m = c.getMethod("main", String[].class);
        m.invoke(null, (Object) a);
    }

    /**
     * 解密目标类名 — 双层保护
     * 第1层：L8 ASM AES-256-CBC 运行时解密
     * 第2层：调用栈验证阻止外部解密
     *
     * 此字符串在构建时被L8替换为 StringDecryptor.decrypt("base64...")
     */
    private static String _d() {
        return "com.github.balloonupdate.mcpatch.client.Main";
    }
}
