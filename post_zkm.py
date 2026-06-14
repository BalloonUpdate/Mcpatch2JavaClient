#!/usr/bin/env python3
"""
Post-ZKM Processing Script v2 for Mcpatch2JavaClient v4.0.0

简化版：不再尝试直接修补class文件常量池（太脆弱）
仅负责：
1. 计算关键class文件的SHA-256哈希（输出给Gradle ASM注入）
2. 输出构建信息

Gradle postProcess任务负责通过ASM可靠地注入哈希到IntegrityChecker

Usage: python3 post_zkm.py <input.jar>
"""

import sys
import os
import hashlib
import base64
import zipfile


def compute_class_hash(data):
    """计算class文件的SHA-256哈希（Base64编码）"""
    h = hashlib.sha256(data).digest()
    return base64.b64encode(h).decode()


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 post_zkm.py <input.jar>")
        sys.exit(1)

    jar_path = sys.argv[1]
    if not os.path.exists(jar_path):
        print(f"Error: JAR file not found: {jar_path}")
        sys.exit(1)

    print(f"Post-ZKM Analysis: {jar_path}")
    print(f"  Size: {os.path.getsize(jar_path) / 1024 / 1024:.1f} MB")

    # 读取JAR中的所有条目
    entries = {}
    with zipfile.ZipFile(jar_path, 'r') as zf:
        for name in zf.namelist():
            entries[name] = zf.read(name)

    # 查找关键class文件
    # 注意：ZKM可能已经重命名/移动了这些类，我们需要通过特征匹配
    critical_patterns = [
        # 原始路径 — ZKM前
        "entry/Boot.class",
        "entry/SecurityGuard.class",
        "entry/IntegrityChecker.class",
        "entry/StringDecryptor.class",
        "entry/BuildInfo.class",
        # ZKM后可能的新路径 (o.o包 + non-ASCII名称)
    ]

    # 计算找到的关键class的哈希
    hashes = {}
    for name, data in entries.items():
        if not name.endswith('.class'):
            continue
        # 查找entry包下的class（可能已被ZKM移动）
        for pattern in critical_patterns:
            if name == pattern:
                h = compute_class_hash(data)
                hashes[name] = h
                print(f"  Hash: {name} = {h[:16]}...")

    # 同时查找包含特定特征的class（ZKM后路径变化）
    # 通过字符串特征找到Boot类（包含premain方法签名）
    for name, data in entries.items():
        if not name.endswith('.class'):
            continue
        if name in hashes:
            continue

        # 检查class文件是否包含premain方法
        # 方法签名: (Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V
        if b'premain' in data and b'java/lang/instrument/Instrumentation' in data:
            h = compute_class_hash(data)
            hashes[name] = h
            print(f"  Hash (Boot): {name} = {h[:16]}...")

        # 检查StringDecryptor特征
        if b'decrypt' in data and b'AES/CBC/PKCS5Padding' in data:
            h = compute_class_hash(data)
            hashes[name] = h
            print(f"  Hash (Decryptor): {name} = {h[:16]}...")

    # 输出哈希表（Gradle会读取这些）
    hash_str = ",".join(f"{k}:{v}" for k, v in sorted(hashes.items()))
    print(f"\n  HASH_TABLE={hash_str}")

    # 统计JAR信息
    class_count = sum(1 for n in entries if n.endswith('.class'))
    total_size = sum(len(d) for d in entries.values())
    print(f"\n  Classes: {class_count}")
    print(f"  Total uncompressed: {total_size / 1024 / 1024:.1f} MB")
    print(f"Post-ZKM analysis complete!")


if __name__ == '__main__':
    main()
