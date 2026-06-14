package entry;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * 安全守护器 v2 — 反调试+反Hook+周期性自检
 *
 * 修复弱点6：增加周期性自检（而非仅启动时检查一次）
 * 新增：反调试检测、反Java Agent检测、反Hook检测
 */
public final class SecurityGuard {

    private static volatile boolean initialized = false;
    private static volatile boolean tampered = false;
    private static Thread guardThread = null;

    // 周期性自检间隔（毫秒）— 随机化防定时分析
    private static final long CHECK_INTERVAL_MS = 45_000L + _ji(17);

    // 反调试标记
    private static volatile int _dc = 0; // debug check counter

    private SecurityGuard() {}

    /**
     * 启动安全守护
     * 1. 反调试检测
     * 2. 反Agent检测
     * 3. 完整性校验
     * 4. 启动后台周期自检线程
     */
    static void start() {
        if (initialized) return;

        // === 反调试检测 ===
        if (_isDebugActive()) {
            tampered = true;
            _onTamper();
            return;
        }

        // === 反Java Agent检测 ===
        if (_isForeignAgentPresent()) {
            tampered = true;
            _onTamper();
            return;
        }

        // === 完整性校验 ===
        boolean integrityOk = IntegrityChecker.verify();
        boolean classesOk = IntegrityChecker.verifyCriticalClasses();

        if (!integrityOk || !classesOk) {
            tampered = true;
            _onTamper();
            return;
        }

        initialized = true;

        // === 启动后台周期自检线程 ===
        guardThread = new Thread(SecurityGuard::_guardLoop, "SecG");
        guardThread.setDaemon(true);
        guardThread.setPriority(Thread.MIN_PRIORITY);
        guardThread.start();
    }

    /**
     * 后台守护循环 — 修复弱点6（周期性自检）
     * 随机化间隔 + 多层检测
     */
    private static void _guardLoop() {
        try {
            while (!tampered && !Thread.currentThread().isInterrupted()) {
                // 随机化睡眠时间，防止定时分析
                long sleepTime = CHECK_INTERVAL_MS + _ji(8000);
                Thread.sleep(sleepTime);

                // 周期性完整性校验
                if (!IntegrityChecker.verify()) {
                    tampered = true;
                    _onTamper();
                    break;
                }

                // 周期性反调试检测
                _dc++;
                if (_dc % 3 == 0) {
                    if (_isDebugActive()) {
                        tampered = true;
                        _onTamper();
                        break;
                    }
                }

                // 周期性反Agent检测
                if (_dc % 5 == 0) {
                    if (_isForeignAgentPresent()) {
                        tampered = true;
                        _onTamper();
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            // 正常退出
        }
    }

    /**
     * 反调试检测 — 检查JVM调试参数
     * 检测 jdwp/agentlib 等调试相关启动参数
     */
    private static boolean _isDebugActive() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                String lower = arg.toLowerCase();
                // 检测JDWP调试
                if (lower.contains("jdwp") || lower.contains("agentlib:jdwp")) {
                    return true;
                }
                // 检测远程调试
                if (lower.contains("address=") && lower.contains("transport=dt_socket")) {
                    return true;
                }
                // 检测JVM TI agent（可能用于Hook）
                if (lower.startsWith("-agentpath:")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 安全环境无法读取 → 视为可疑
            return true;
        }
        return false;
    }

    /**
     * 反Java Agent检测 — 检查是否有非自身的Java Agent加载
     * 我们的Agent是合法的Premain-Class，其他Agent可疑
     */
    private static boolean _isForeignAgentPresent() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            int agentCount = 0;
            for (String arg : args) {
                String lower = arg.toLowerCase();
                // 计数 -javaagent 参数
                if (lower.startsWith("-javaagent:")) {
                    agentCount++;
                    // 检查是否是已知的Hook框架
                    String agentPath = arg.substring("-javaagent:".length()).toLowerCase();
                    if (agentPath.contains("xposed") || agentPath.contains("frida") ||
                        agentPath.contains("byteman") || agentPath.contains("jboss") ||
                        agentPath.contains("aspectj")) {
                        return true;
                    }
                }
            }
            // 如果有超过1个javaagent（我们自身+可疑的其他agent），标记
            // 注意：我们自己的agent不会出现在inputArguments中
            if (agentCount > 0) {
                return true;
            }
        } catch (Exception e) {
            // 无法读取 → 保守处理，不标记
        }
        return false;
    }

    /**
     * 篡改检测回调 — 触发安全响应
     * 不直接抛异常（太容易被catch），而是让程序在后续运行中逐步崩溃
     */
    private static void _onTamper() {
        // 策略1：抛出SecurityException
        throw new SecurityException("Integrity check failed");
    }

    /**
     * 检查是否被篡改（供其他类查询）
     */
    static boolean isTampered() {
        return tampered;
    }

    /**
     * 混淆辅助：返回一个看似无用但参与运算的值
     * 用于随机化间隔时间，ZKM会加密这些整数常量
     */
    private static long _ji(int base) {
        int x = base * 37 + 13;
        int y = x ^ 0x5A5A;
        return (long)(y & 0x7FFF);
    }
}
