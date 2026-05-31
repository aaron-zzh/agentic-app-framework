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
| `sub` | string | 官方签发的用户唯一标识（user_id），格式为 `aaf_{16hex}_{8checksum}` |
| `iss` | string | 签发者（`aaf.xuejiai.com`） |
| `iat` | number | 签发时间（Unix timestamp） |
| `exp` | number | 过期时间（Unix timestamp） |
| `tier` | string | 授权等级：`premium` / `enterprise` |
| `org` | string | 组织名称（可选，企业版） |
| `features` | string[] | 授权的高级模块/能力码，只放商业高级模块，不放 RBAC 权限码 |
| `owner` | boolean | 是否为官方服务 owner 授权。仅官方服务实例设置为 `true` |

示例：
```json
{
  "sub": "aaf_7f4a12c8e91b03d2_ab7ae872",
  "iss": "aaf.xuejiai.com",
  "iat": 1714924800,
  "exp": 1746460800,
  "tier": "premium",
  "org": "Acme Corp",
  "owner": false,
  "features": ["developer"]
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

### 前端订阅入口

当前实现提供只读状态接口：

- `GET /api/license/current`：返回当前 `premium`、`owner`、`tier`、`userId`、`expiresAt`、`upgradeUrl` 与授权文件放置位置。
- `GET /api/license/source-code`：下载当前实例可用的源码包，要求本地 license `features` 包含 `source-download`。
- 授权文件仍由官方签发，文件名固定为 `license.jwt`。
- 本地开发或私有部署时，优先放置到 `~/.aaf/license.jwt`；也可以放置到应用工作目录的 `./config/license.jwt`。
- `upgradeUrl` 由本地后端返回，当前默认固定为 `https://www.xuejiai.com`，前端不得自行拼接官方订阅地址。

前端工作区侧边栏底部展示框架等级标记。点击后打开订阅管理弹层，弹层同时展示：

- 当前授权状态和授权文件位置。
- 官方升级入口按钮，地址来自 `/api/license/current.upgradeUrl`。

注意：官方订阅购买和 `license.jwt` 签发不在本地开源实例内完成。前端只通过本地 `/api/license/current` 获取状态和官方入口；真正解锁高级功能仍以经过签名校验的 `license.jwt` 为准。

### 官方服务入口

当 AAF 部署为雪稽 AI 官方商业服务时，license key 需要额外携带 `owner=true`。本地前端和后端都基于该标记控制官方入口：

- 前端侧边栏仅在 `license.owner === true` 时展示“官方服务”菜单。
- `/official/portal` 提供客户门户入口，用于订阅、账单与授权文件下载的服务承载页。
- `/official/admin` 提供官方运营管理入口，用于开发者运营、套餐、兑换码和授权签发。
- `GET /api/official/console/summary` 提供官方控制台摘要，受 owner 授权保护。
- `POST /api/official/console/licenses` 签发 `license.jwt`，参数包含 `subject`、`tier`、`org`、`expiresAt`、`owner`；`subject` 留空时自动生成官方格式 user_id，手工传入时必须通过格式校验。
- 签发时可配置 `features`，仅包含商业高级模块/能力码，不承载 RBAC 权限；未知 feature 会被拒绝签发。
- 签发动作写入 `sys_audit_log`，记录 subject、tier、owner 与 features。
- `GET /api/official/console/source-code` 是 owner 控制台内的源码包下载入口；普通客户实例使用 `/api/license/source-code`，二者都要求 `features` 包含 `source-download`，源码包路径由 `aaf.license.source-archive-path` 配置。
- 官方运营管理后端接口使用 `@LicenseOwnerRequired` 保护；普通 Premium 授权不能访问 owner-only 管理接口。
- 自部署实例即使拥有源代码，也不会因为普通 Pro 授权看到官方服务入口。

签发接口依赖官方服务实例配置 RSA 私钥：

```yaml
aaf:
  license:
    signing:
      issuer: aaf.xuejiai.com
      private-key: ${AAF_LICENSE_PRIVATE_KEY}
    identity:
      prefix: ${AAF_LICENSE_ID_PREFIX:aaf_}
      checksum-salt: ${AAF_LICENSE_ID_CHECKSUM_SALT}
      seed-salt: ${AAF_LICENSE_ID_SEED_SALT}
    source-archive-path: ${AAF_SOURCE_ARCHIVE_PATH:}
```

`private-key` 使用 PKCS#8 RSA 私钥内容，支持带 `-----BEGIN PRIVATE KEY-----` 头尾的 PEM，也支持去掉头尾后的 Base64。私钥只配置在官方服务实例，不随开源包分发。
`identity.checksum-salt` 与 `identity.seed-salt` 用于生成 user_id 校验段和功能耦合 seed。开源包保留默认值方便本地开发，官方服务必须用私有配置覆盖；客户实例验签时需要使用同一套 identity 配置，否则 `identityValid=false`，高级能力进入降级 seed。

标准高级模块码由 `LicenseFeature` 统一登记：

| Feature | 说明 |
|---------|------|
| `developer` | 开发者商业化模块 |
| `source-download` | 源码包下载 |
| `managed-gateway` | 托管模型网关 |
| `official-console` | 官方服务控制台 |

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
@PremiumRequired("高级模块")
public class AdvancedController {
    // 类上标记时，类内入口默认需要 Premium 授权
}

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
                LicensePortal.UPGRADE_URL
            );
        }
        return pjp.proceed();
    }
}
```

当前实现支持类级和方法级两种门控：

- 类级：适合整个 Controller、Service 或模块入口都属于高级能力。
- 方法级：适合保留公开升级入口，只拦截具体高级操作。
- 若类和方法同时标记，以方法级 `value` 作为错误提示，避免重复拦截。
- `@LicenseOwnerRequired`：适合官方服务运营接口，要求 license 中 `owner=true`，通常与 `@PremiumRequired` 叠加使用。
- `@FeatureRequired("developer")`：适合按高级模块/能力码门控，要求 license `features` 包含对应值。官方签发接口只允许登记在 `LicenseFeature` 中的高级模块码。

首个演示模块为 `developer`：

- 本地 `developer` 模块用于高级模块演示和开发者运营管理，不作为本地给当前实例自助开通 Pro 的入口。
- 需要 Premium：开发者账户、当前订阅、Token 池、Gateway Key、子代理、运营兑换码管理。
- 需要 Feature：上述开发者高级接口同时要求 `features` 包含 `developer`。
- 通用 CRUD 示例：`/api/developer/admin/subscription-plans` 继承 `BaseCrudController`，提供分页、`/_query`、详情、创建、更新、删除、选择器、元数据、导出等标准接口；权限码使用 `developer:subscription-plan:{action}`。
- 管理员代开/调整：`POST /api/developer/admin/accounts/{userId}/subscribe` 会为指定用户创建开发者账户，并将旧 ACTIVE 订阅置为 `CANCELLED` 后创建新订阅。
- 未授权访问时返回统一 `Result`，HTTP 状态为 `403`，错误信息包含功能名称和升级地址。

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

`sub/user_id` 不只是展示标识，而是高级能力的稳定依赖。官方签发服务生成的 `user_id` 带校验段；运行时会先校验格式，再派生 `couplingSeed`。如果用户自行填写普通字符串，签名即使有效，`identityValid=false`，高级能力会使用降级 seed。

| 耦合点 | 方式 | 破解后果 |
|--------|------|----------|
| Agent 调度 seed | 官方格式 `user_id` 派生 `couplingSeed` | 非法 user_id 进入降级 seed，调度行为与正版不同 |
| 采样策略参数 | `user_id` 派生初始化值 | 采样结果偏差 |
| Trace ID 生成 | `user_id` 作为前缀 | 日志可追溯 |
| 输出元数据 | 隐式携带 `user_id` 哈希 | 结果可溯源 |

### 5.2 实现示例

```java
public class AgentScheduler {
    private final Random random;
    
    public AgentScheduler() {
        // 非官方格式 user_id 会得到降级 seed=0，行为与正版不同
        long seed = License.get().getCouplingSeed();
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
