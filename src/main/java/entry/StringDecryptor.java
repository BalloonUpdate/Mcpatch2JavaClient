package entry;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * L8 AES-256-CBC 字符串解密器 v2
 *
 * 修复弱点4：密钥多层编码 + 调用栈验证
 * - 主密钥由构建时ASM注入到 _KEY_B64 字段
 * - 运行时密钥组装：从 _PART_A + _PART_B + _KEY_B64 三部分动态重组
 * - 不存在单一明文密钥字段可被反编译读取
 * - 调用栈验证阻止逆向工具直接调用decrypt()
 */
public final class StringDecryptor {

    // 由构建时ASM字符串加密任务注入（L8加密后的密文密钥）
    static String _KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="; // placeholder, ASM overwrites

    // 修复弱点4：密钥拆分存储，运行时重组
    // _PART_A / _PART_B 由构建时随机生成并注入
    private static final int[] _PART_A = {0, 0, 0, 0};  // placeholder, build overwrites
    private static final int[] _PART_B = {0, 0, 0, 0};  // placeholder, build overwrites

    private static volatile byte[] _cachedKey = null;

    /** 调用栈验证状态 — 一旦验证失败永久锁定 */
    private static volatile int _gs = 0; // 0=未检查, 1=通过, -1=失败

    private StringDecryptor() {}

    /**
     * 解密L8 ASM加密的字符串
     * 格式: Base64(IV[16] + HMAC-SHA256[32] + AES-CBC-Ciphertext)
     *
     * 增加调用栈验证：阻止jshell/groovy/BeanShell等逆向工具直接调用
     */
    public static String decrypt(String encrypted) {
        _v(); // 调用栈验证
        try {
            byte[] masterKey = _k();
            byte[] data = Base64.getDecoder().decode(encrypted);

            // 提取IV (前16字节)
            byte[] iv = new byte[16];
            System.arraycopy(data, 0, iv, 0, 16);

            // 提取HMAC (16-48字节)
            byte[] hmac = new byte[32];
            System.arraycopy(data, 16, hmac, 0, 32);

            // 提取密文 (48字节起)
            byte[] ciphertext = new byte[data.length - 48];
            System.arraycopy(data, 48, ciphertext, 0, ciphertext.length);

            // HMAC验证
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            byte[] computedHmac = mac.doFinal(ciphertext);
            if (!java.security.MessageDigest.isEqual(hmac, computedHmac)) {
                throw new SecurityException("HMAC verification failed");
            }

            // AES-256-CBC解密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 调用栈验证 — 修复弱点：阻止外部逆向工具调用decrypt()
     *
     * 检测策略：
     * 1. 黑名单：已知逆向/脚本工具（jshell, groovy, bsh, jython等）
     * 2. 反射检测：通过反射调用decrypt的，要求更深调用栈中有应用代码
     * 3. 调用深度：正常调用栈深度3-8层，异常深度拦截
     */
    private static void _v() {
        if (_gs < 0) {
            throw new SecurityException("Guard locked");
        }
        if (_gs > 0) return; // 已验证通过

        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();

            // 正常调用栈: getStackTrace → _v → decrypt → caller
            // 所以 caller 在 st[3]
            if (st.length < 4) {
                _gs = -1;
                throw new SecurityException("Stack too shallow");
            }

            // 检查1：黑名单工具
            String[] _b = {
                "jshell", "jdk.jshell",
                "groovy", "org.codehaus.groovy",
                "bsh", "BeanShell",
                "jython", "org.python",
                "clojure", "clojure.lang",
                "jnlp", "javax.swing",
                "org.jetbrains", "com.intellij"
            };

            for (int i = 3; i < Math.min(st.length, 12); i++) {
                String cn = st[i].getClassName();
                for (String bl : _b) {
                    if (cn.contains(bl)) {
                        _gs = -1;
                        throw new SecurityException("Blocked");
                    }
                }
            }

            // 检查2：反射调用需要二次验证
            String c3 = st[3].getClassName();
            if (c3.contains("reflect.Method") || c3.contains("sun.reflect")) {
                // 反射调用 — 检查更深层是否有应用代码
                boolean appFound = false;
                for (int i = 4; i < Math.min(st.length, 15); i++) {
                    String si = st[i].getClassName();
                    // ZKM混淆后的类名特征：非ASCII字符或o.o包
                    if (si.startsWith("entry.") ||
                        si.startsWith("com.github.balloonupdate.") ||
                        si.startsWith("o.o") ||
                        _isObscured(si)) {
                        appFound = true;
                        break;
                    }
                }
                if (!appFound) {
                    _gs = -1;
                    throw new SecurityException("Reflection blocked");
                }
            }

            _gs = 1; // 验证通过
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            _gs = -1;
            throw new SecurityException("Verification error");
        }
    }

    /**
     * 判断类名是否被ZKM混淆（包含非ASCII字符或极短名称）
     */
    private static boolean _isObscured(String className) {
        if (className.length() <= 3) return true;
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (c > 127) return true; // non-ASCII
        }
        return false;
    }

    /**
     * 修复弱点4：从拆分的3部分重组密钥，不存储单一明文密钥
     * 组合顺序: _PART_A XOR _PART_B → 与 _KEY_B64 解码后异或 → 最终密钥
     */
    private static byte[] _k() {
        if (_cachedKey != null) return _cachedKey;
        synchronized (StringDecryptor.class) {
            if (_cachedKey != null) return _cachedKey;

            // 第1层：从 _PART_A 和 _PART_B 重组中间密钥
            byte[] intermediate = new byte[_PART_A.length];
            for (int i = 0; i < _PART_A.length; i++) {
                intermediate[i] = (byte) (_PART_A[i] ^ _PART_B[i]);
            }

            // 第2层：Base64解码主密钥
            byte[] mainKeyPart = Base64.getDecoder().decode(_KEY_B64);

            // 第3层：异或组合得到最终AES密钥
            byte[] finalKey = new byte[32];
            for (int i = 0; i < 32; i++) {
                finalKey[i] = (byte) (mainKeyPart[i] ^ intermediate[i % intermediate.length]);
            }

            _cachedKey = finalKey;
            return _cachedKey;
        }
    }
}
