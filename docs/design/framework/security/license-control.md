---
level: Practice
layer: Model
purpose: AAF 商业授权控制技术设计
status: draft
version: 0.1.0
date: 2026-05-06
author: AaronZZH
---

# 商业授权控制设计（License Control）

> 本文档聚焦**技术实现**。需求背景、用户故事、验收标准、技术约束见 [需求规格](../../../task/v0.1.0/AAF-018/requirement.md)。

## 0. 设计边界

| 边界 | 说明 |
|------|------|
| License 控制什么 | 高级功能解锁、配额参数、插件注册 |
| License 不控制什么 | 大模型 API Key（用户自行配置，直连模型厂商） |
| 与 Access Control 的关系 | 正交：License 管"付费了吗"，Access Control 管"有权限吗" |

## 1. JWT 结构设计

### 1.1 Token 结构

<!-- DECISION: JWT 签名算法
选项：
- A) RS256（RSA + SHA256）— 非对称，公钥可公开
- B) ES256（ECDSA）— 更短的签名，同等安全性
- C) HS256（HMAC）— 对称，密钥需保密
倾向：A) RS256，公钥可内嵌到代码中
-->

```
Header: { "alg": "RS256", "typ": "JWT" }
Payload: { claims }
Signature: RS256(header.payload, private_key)
```

### 1.2 Claims 定义

<!-- DECISION: Claims 字段设计
需要哪些字段？是否需要 features 白名单？
-->

| Claim | 类型 | 说明 |
|-------|------|------|
| `sub` | string | 用户唯一标识（user_id） |
| `iss` | string | 签发者（`aaf.xuejiai.com`） |
| `iat` | number | 签发时间（Unix timestamp） |
| `exp` | number | 过期时间（Unix timestamp） |
| `tier` | string | 授权等级：`premium` / `enterprise` |
| `org` | string | 组织名称（可选，企业版） |
| `features` | string[] | 可选，功能白名单（未来扩展） |

示例：
```json
{
  "sub": "user_12345",
  "iss": "aaf.xuejiai.com",
  "iat": 1714924800,
  "exp": 1746460800,
  "tier": "premium",
  "org": "Acme Corp"
}
```

### 1.3 公钥分发

<!-- DECISION: 公钥存放位置
选项：
- A) 内嵌到代码中（编译时固定）
- B) 配置文件（可替换，但破解者也能替换）
- C) 多处冗余内嵌（提高替换成本）
倾向：C) 多处冗余
-->

- 公钥内嵌到框架代码中
- 多个位置冗余存储，校验时交叉验证
- 破解者需同步修改所有位置

## 2. 加载流程

```
框架启动
  ↓
扫描配置目录（~/.aaf/license.jwt 或 ./config/license.jwt）
  ↓
┌─ 文件存在？
│   ├─ 否 → LICENSE.is_premium=false，日志 "running in free mode"
│   └─ 是 → 解析 JWT
│            ↓
│       ┌─ 签名有效？
│       │   ├─ 否 → 警告日志，降级 free mode
│       │   └─ 是 → 检查过期时间
│       │            ↓
│       │       ┌─ 已过期？
│       │       │   ├─ 是 → 警告日志，降级 free mode
│       │       │   └─ 否 → 设置 LICENSE 对象
│       │       │            - is_premium = true
│       │       │            - user_id = sub
│       │       │            - tier = tier
└───────┴───────┴─→ 继续启动流程
```

## 3. 全局 LICENSE 对象

```java
public final class License {
    private static final License INSTANCE = new License();
    
    private volatile boolean premium = false;
    private volatile String userId = null;
    private volatile String tier = "free";
    private volatile Instant expiresAt = null;
    
    public static License get() { return INSTANCE; }
    
    public boolean isPremium() { return premium; }
    public String getUserId() { return userId; }
    public String getTier() { return tier; }
    
    // 仅启动时调用一次
    void initialize(DecodedJWT jwt) { ... }
}
```

运行时访问：`License.get().isPremium()` — O(1) 内存读取。

## 4. 功能门控实现

### 4.1 注解 + AOP（入口层）

```java
@PremiumRequired
public void advancedFeature() {
    // 高级功能实现
}

@Aspect
public class LicenseAspect {
    @Around("@annotation(PremiumRequired)")
    public Object checkLicense(ProceedingJoinPoint pjp) {
        if (!License.get().isPremium()) {
            throw new LicenseRequiredException(
                "此功能需要 Premium 授权",
                "https://aaf.xuejiai.com/pricing"
            );
        }
        return pjp.proceed();
    }
}
```

### 4.2 配置参数动态设置

```java
public class DefaultConfig {
    public int getMaxTokens() {
        return License.get().isPremium() ? 8192 : 2048;
    }
    
    public int getMaxConcurrentAgents() {
        return License.get().isPremium() ? 20 : 3;
    }
}
```

### 4.3 插件注册过滤

```java
public void registerPlugins(List<Plugin> plugins) {
    for (Plugin plugin : plugins) {
        if (plugin.requiresPremium() && !License.get().isPremium()) {
            log.debug("Skipping premium plugin: {}", plugin.getName());
            continue;
        }
        registry.register(plugin);
    }
}
```

## 5. 分散式权限耦合

> 目标：删除显式检查后功能仍异常，提高破解成本。

<!-- DECISION: 耦合点选择
需要确定哪些关键算法使用 user_id 作为 seed/trace
-->

### 5.1 耦合点

| 耦合点 | 方式 | 破解后果 |
|--------|------|----------|
| Agent 调度 seed | `hash(user_id)` 作为随机种子 | 调度行为不可预测 |
| 采样策略参数 | `user_id` 派生初始化值 | 采样结果偏差 |
| Trace ID 生成 | `user_id` 作为前缀 | 日志可追溯 |
| 输出元数据 | 隐式携带 `user_id` 哈希 | 结果可溯源 |

### 5.2 实现示例

```java
public class AgentScheduler {
    private final Random random;
    
    public AgentScheduler() {
        String userId = License.get().getUserId();
        // user_id 为 null 时 seed=0，行为与正版不同
        long seed = userId != null ? userId.hashCode() : 0L;
        this.random = new Random(seed);
    }
}
```

## 6. 相关文档

- [需求规格](../../../task/v0.1.0/AAF-018/requirement.md) — 用户故事、验收标准、技术约束
- [访问控制设计](access-control.md) — 认证、授权（正交维度）
- [安全架构设计](security.md) — 加密、审计（正交维度）

## 7. 决策记录

| 日期 | 决策点 | 结论 | 理由 |
|------|--------|------|------|
| 2026-05-06 | 文档分工 | Design 聚焦实现，PRD 聚焦需求 | 避免重复，单一真理源 |
| | JWT 签名算法 | 待定（倾向 RS256） | |
| | Claims 字段 | 待定 | |
| | 公钥存放 | 待定（倾向多处冗余） | |
| | 耦合点选择 | 待定 | |
