package entry;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * JAR完整性校验器 v2 — 逐类哈希校验
 *
 * 解决"鸡生蛋"问题：
 * - 不对整个JAR计算哈希（注入哈希后哈希值会变）
 * - 改为对关键class文件逐个计算SHA-256哈希
 * - 修改IntegrityChecker不影响其他class文件的哈希
 *
 * 哈希注入方式：
 * - 由Gradle postProcess任务在ZKM处理后通过ASM注入
 * - _HASHES 字段格式: className1:hash1,className2:hash2,...
 * - 存储时经L8加密（AES-256-CBC），运行时解密
 */
public final class IntegrityChecker {

    /**
     * 逐类哈希表 — 由构建时ASM注入
     * 格式: "classPath1:sha256_1,classPath2:sha256_2,..."
     * 此字符串将被L8 ASM加密，运行时由StringDecryptor解密
     */
    static String _HASHES = ""; // placeholder, ASM injects at build time

    private IntegrityChecker() {}

    /**
     * 验证JAR中关键class文件的SHA-256哈希
     * @return true=所有哈希校验通过, false=有任何不匹配
     */
    static boolean verify() {
        try {
            // 哈希未注入（空字符串或L8已解密后的空串）→ 跳过
            if (_HASHES == null || _HASHES.isEmpty()) return true;

            Path jarPath = com.github.balloonupdate.mcpatch.client.utils.Env.getJarPath();
            if (jarPath == null) return true; // 开发环境跳过

            // 解析哈希表
            Map<String, String> expected = _parseHashes(_HASHES);
            if (expected.isEmpty()) return true;

            // 逐个验证
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

                for (Map.Entry<String, String> entry : expected.entrySet()) {
                    String classPath = entry.getKey();
                    String expectedHash = entry.getValue();

                    JarEntry je = jar.getJarEntry(classPath);
                    if (je == null) return false; // class文件不存在

                    // 读取class文件内容并计算哈希
                    byte[] classBytes;
                    try (InputStream is = jar.getInputStream(je)) {
                        classBytes = is.readAllBytes();
                    }

                    sha256.reset();
                    byte[] hash = sha256.digest(classBytes);
                    String actualHash = Base64.getEncoder().encodeToString(hash);

                    if (!actualHash.equals(expectedHash)) {
                        return false; // 哈希不匹配
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证JAR中关键class文件的存在性和最小大小
     * 防止攻击者删除安全相关class
     *
     * 注意：ZKM可能重命名class文件，所以检查的是混淆后的路径
     * 此方法使用运行时类路径推断
     */
    static boolean verifyCriticalClasses() {
        try {
            Path jarPath = com.github.balloonupdate.mcpatch.client.utils.Env.getJarPath();
            if (jarPath == null) return true;

            // 通过已加载类的ProtectionDomain获取实际JAR内路径
            Class<?>[] criticalClasses = {
                Boot.class,
                SecurityGuard.class,
                IntegrityChecker.class,
                StringDecryptor.class
            };

            try (JarFile jar = new JarFile(jarPath.toFile())) {
                for (Class<?> cls : criticalClasses) {
                    // 获取类在JAR中的路径
                    String className = cls.getName();
                    String classPath = className.replace('.', '/') + ".class";

                    JarEntry je = jar.getJarEntry(classPath);
                    if (je == null) return false;
                    if (je.getSize() < 300) return false; // 异常小的class文件
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析哈希表字符串
     * 格式: "path1:hash1,path2:hash2,..."
     */
    private static Map<String, String> _parseHashes(String hashes) {
        Map<String, String> map = new HashMap<>();
        if (hashes == null || hashes.isEmpty()) return map;

        String[] pairs = hashes.split(",");
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon > 0 && colon < pair.length() - 1) {
                String path = pair.substring(0, colon);
                String hash = pair.substring(colon + 1);
                map.put(path, hash);
            }
        }
        return map;
    }
}
