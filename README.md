# Mcpatch2JavaClient

Mcpatch2JavaClient 是一个 Minecraft 客户端自动更新程序，支持从云端服务器拉取配置并管理客户端文件更新。项目实现了完整的六层安全防御体系，确保配置传输和存储的端到端安全。

## 功能特性

- **多云端协议支持**：mcpatch://（私有协议）、HTTP/HTTPS、WebDAV
- **多地址故障转移**：可配置多个更新服务器地址，自动依次尝试
- **六层安全防御**：RSA-PSS 签名验证、HMAC-SHA256 请求签名、密钥碎片化 XOR 还原、AES-256-GCM 加密传输、时间戳防重放、HTTPS 证书锁定
- **云端配置管理**：从远程服务器拉取加密配置，支持「云端 → 缓存 → 本地」三级降级
- **密钥自动轮换**：AES 密钥更换时自动检测并重新初始化本地碎片
- **分片多线程下载**：大文件自动分片并行下载，智能线程数策略
- **防盗链鉴权**：支持阿里云 ESA A 方案鉴权
- **完全硬编码配置**：所有敏感值以 XOR+LCG 流密码编码存储在 JAR 中，无明文泄露风险

## 安全架构

```
┌──────────────────────────────────────────────────────────────┐
│                    六层安全防御体系                            │
├──────────────────────────────────────────────────────────────┤
│ Layer 1 │ RSA-PSS 签名验证        │ 防篡改（动态获取公钥）     │
│ Layer 2 │ HMAC-SHA256 请求签名    │ 防直接调用（碎片化密钥）   │
│ Layer 3 │ 密钥碎片化 XOR 还原     │ 防逆向（3碎片分散存储）    │
│ Layer 4 │ AES-256-GCM 加密传输    │ 防窃听（GCM 认证加密）    │
│ Layer 5 │ 秒级时间戳防重放        │ 服务端校验                │
│ Layer 6 │ HTTPS 证书锁定         │ 防中间人                  │
└──────────────────────────────────────────────────────────────┘
```

### 密钥碎片化存储（Layer 3）

AES-256 密钥被拆分为 3 个 XOR 碎片，分散存储在不同位置，单独获取任意 2 个碎片无法还原密钥：

| 碎片 | 存储位置 | 伪装方式 |
|------|----------|----------|
| Fragment1 | `BuildInfo.BUILD_SIGNATURE` | 构建签名校验值 |
| Fragment2 | `ThemeConfig.THEME_ACCENT_COLORS` | 界面主题色值数组 |
| Fragment3 | `.minecraft/assets/indexes/vXXXX` | Minecraft 资源索引文件 |

还原公式：`AES Key = Fragment1 ⊕ Fragment2 ⊕ Fragment3`

HMAC-SHA256 签名密钥同样采用 3 碎片 XOR 方式，全部以 XOR+LCG 流密码编码存储在 `HardcodedConfig` 中。

### 加密通信流程

```
Client                                         Server
  │                                               │
  │  1. GET /api/client?encrypt=true              │
  │     Authorization: Bearer <api-key>           │
  │     X-Timestamp: <unix-seconds>               │
  │     X-Signature: HMAC-SHA256(timestamp+key+path, hmacSecret)
  │ ────────────────────────────────────────────► │
  │                                               │
  │  2. 200 OK                                    │
  │     X-Encryption-Algorithm: AES-256-GCM       │
  │     X-Signature-Algorithm: RSA-SHA256-PSS     │
  │     X-AES-IV: <12-byte-nonce-hex>             │
  │     X-AES-Fingerprint: <sha256-of-aes-key>    │
  │     X-Config-Signature: <RSA-PSS-base64>      │
  │     Body: <ciphertext + 16-byte-GCM-auth-tag> │
  │ ◄──────────────────────────────────────────── │
  │                                               │
  │  3. 验证 AES 指纹 → GCM 解密 → PSS 签名验证   │
  │                                               │
```

### XOR+LCG 流密码编码

所有硬编码敏感字符串（API 地址、密钥、碎片等）均通过 XOR+LCG 流密码编码为 `int[]` 数组存储，运行时动态解码。JAR 反编译后无法直接搜索到任何明文配置值。

编码原理：
1. 种子密钥：`key = K1 ^ K2`（两个 16 位常量异或）
2. 流生成：`key = (key * 1103515245 + 12345) & 0x7FFFFFFF`（LCG 线性同余）
3. 编码：`enc[i] = plain[i] ^ (key & 0xFF)`

## 项目结构

```
src/main/java/com/github/balloonupdate/mcpatch/client/
├── Main.java                 # 主入口，Fragment3 自动初始化与密钥轮换检测
├── Work.java                 # 更新任务核心逻辑
├── HardcodedConfig.java      # 硬编码配置（XOR+LCG 编码，JAR 无明文泄露）
├── CloudConfigFetcher.java   # 云端配置拉取器（六层安全体系实现）
├── CloudCrypto.java          # 加解密工具（AES-256-GCM、RSA-PSS）
├── SecureAesHelper.java      # 安全加解密助手（密钥安全还原、HMAC 签名）
├── KeySharding.java          # 密钥碎片化 XOR 组装
├── FragmentStore.java        # Fragment3 本地存储管理
├── BuildInfo.java            # 构建信息（伪装存储 Fragment1）
├── ThemeConfig.java          # 主题配置（伪装存储 Fragment2）
├── BalloonUpdateMain.java    # 兼容入口
├── ChunkedDownloader.java    # 分片下载器
├── ParallelDownloader.java   # 并行下载器
├── FocusManager.java         # 焦点管理器
├── config/
│   └── AppConfig.java        # 配置解析（敏感值从 HardcodedConfig 读取）
├── exceptions/
│   └── CloudConfigException.java  # 云端配置异常
├── logging/                  # 日志系统
├── network/                  # 网络层
├── ui/                       # 界面
└── utils/                    # 工具类
```

## 配置说明

### mcpatch.yml

所有配置项均有默认值，云控场景下仅需极简配置：

```yaml
# 更新服务器地址
urls:
  - mcpatch://127.0.0.1:6700

# 版本号文件路径
version-file-path: version-label.txt

# 云端配置（敏感参数已硬编码，此处仅控制开关）
cloud-config:
  enabled: true
```

完整配置项参见 [mcpatch.yml 示例](mcpatch.yml)。

### 硬编码配置

云控场景下，所有敏感参数（API 地址、密钥、HMAC 碎片等）以 XOR+LCG 编码存储在 `HardcodedConfig.java` 中，部署时需替换为实际值的编码形式：

| 编号 | 配置项 | 说明 |
|------|--------|------|
| ε1 | URL 列表 | 更新服务器地址 |
| ε4 | API URL | 云控 API 地址 |
| ε5 | API Key | API 访问密钥 |
| ε6 | 缓存文件 | 本地缓存路径 |
| ε7 | HMAC FRAG1 | HMAC 签名碎片 1 |
| ε8 | HMAC FRAG2 | HMAC 签名碎片 2 |
| ε9 | HMAC FRAG3 | HMAC 签名碎片 3 |
| ε10 | Auth API URL | 防盗链鉴权地址 |
| ε12 | AES Key | AES-256 密钥（64 hex = 32 bytes） |

AES 密钥碎片也需同步更新：
- `BuildInfo.BUILD_SIGNATURE` — Fragment1（64 hex 字符串）
- `ThemeConfig.THEME_ACCENT_COLORS` — Fragment2（32 个 int 值）

Fragment3 会在首次启动时自动从 ε12 密钥计算生成，写入本地隐藏文件。

## 构建与部署

### 前置条件

- JDK 21+
- Gradle 8.10+

### 构建

```bash
./gradlew shadowJar
```

产物位于 `build/libs/Mcpatch-0.0.0.jar`。

### 部署步骤

1. 在云控管理后台生成 API 密钥、HMAC 碎片、AES 密钥
2. 使用编码工具将敏感值编码为 XOR+LCG `int[]` 数组
3. 替换 `HardcodedConfig.java`、`BuildInfo.java`、`ThemeConfig.java` 中的编码值
4. 将 AES 密钥导入服务端（POST `/api/security/rotate-aes`）
5. 重新构建 JAR 并分发

## 服务端协议要求

客户端要求服务端支持以下 API 端点和响应格式：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/client?encrypt=true` | GET | 获取加密配置 |
| `/api/security/public-key` | GET | 获取 RSA 公钥 |
| `/api/security/rotate-aes` | POST | 导入/轮换 AES 密钥 |

### 加密响应头

| 响应头 | 说明 |
|--------|------|
| `X-Encryption-Algorithm` | 固定 `AES-256-GCM` |
| `X-Signature-Algorithm` | 固定 `RSA-SHA256-PSS` |
| `X-AES-IV` | GCM Nonce（24 hex = 12 bytes） |
| `X-AES-Fingerprint` | AES 密钥 SHA-256 指纹（冒号分隔或纯 hex） |
| `X-Config-Signature` | RSA-PSS 签名（Base64 编码） |
| `X-Config-Version` | 配置版本号 |

### 加密响应体格式

响应体为原始二进制：`ciphertext + auth_tag`（GCM 认证标签 16 字节追加在密文末尾）。

### HMAC 签名算法

```
签名原文 = timestamp + apiKey + requestPath
HMAC 密钥 = hex(frag1 ⊕ frag2 ⊕ frag3) 的 UTF-8 字节
签名结果 = HMAC-SHA256(签名原文, hmacKey).hexdigest()
```

## 降级策略

```
云端服务器 ──成功──► 使用云端配置
     │
   失败
     │
     ▼
本地缓存 ──有效──► 使用缓存配置（AES-256-GCM 解密）
     │
   过期/损坏
     │
     ▼
fallback-local=true  ──► 使用本地 mcpatch.yml 配置
fallback-local=false ──► 抛出异常
```

## 缓存文件格式

缓存文件采用二进制格式：

```
┌────────────┬───────────────┬───────────────┬──────────────────┐
│ Magic (4B) │ Nonce (12B)   │ AuthTag (16B) │ Ciphertext (变长) │
│ "MCGC"     │ GCM Nonce     │ GCM 认证标签  │ Base64密文数据    │
└────────────┴───────────────┴───────────────┴──────────────────┘
```

## 许可证

请参阅 [LICENSE](LICENSE) 文件。
