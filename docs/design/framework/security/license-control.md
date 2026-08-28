---
level: Practice
layer: Model
purpose: AAF 商业授权控制与开发者商业化技术设计
status: draft
version: 0.2.0
date: 2026-08-23
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

## 6. 开发者商业化与托管额度

> License 控制功能入口，本节控制开发者侧的额度与资格。两者都不是最终安全边界——官方 Gateway 服务端校验才是。

### 6.1 两类计费主体

计费与授权必须分层：

| 主体 | 消耗什么 | 授权来源 |
|------|----------|----------|
| 框架开发者 | AAF 托管模型额度，或自带第三方模型 Key | 开发者授权与订阅 |
| 产品最终用户 | 开发者交付的产品内积分、权益或套餐额度 | 产品侧账户 |

开发者额度不等于最终用户积分；开发者子代理资格不等于产品用户权限。

### 6.2 模块边界

```text
module/developer        开发者商业授权与额度事实
module/ai/gateway       模型代理执行层，合规确认后启用
module/billing/pay      产品最终用户订阅、积分与支付
module/ops              运营后台视图、审计、统计
```

`module/developer` 子域：`license`（授权与托管网关资格）、`subscription`（订阅套餐）、`quota`（Token 池与流水）、`redeem`（兑换码）、`apikey`（Gateway 调用 Key）、`proxy`（子代理与分销资格）。`ops` 只做运营视图，不拥有核心业务事实。

### 6.3 合规边界

当前版本只实现开发者管理，**不实现公网模型代理服务**。面向第三方提供模型代理或转售可能涉及：

- 域名、网站或应用的 ICP 备案或经营性许可
- 面向境内公众提供生成式 AI 服务时的备案或登记
- 调用已备案模型能力时的属地网信办登记，并在产品显著位置公示模型名称与备案号

因此 `developer_api_key` 当前只作为未来 Gateway 的身份凭证管理，不代表已开放模型代理调用。

### 6.4 两层额度模型

```text
AAF 托管模型资源
  → developer_token_account     开发者总 Token 池
    → credit_account            产品最终用户积分账户
```

Gateway 启用后，一次托管模型调用必须同时满足：开发者授权允许 `allow_managed_gateway`、子代理链未超过 `max_proxy_depth`、开发者 Token 池余额充足、最终用户积分或权益充足、模型与地域与内容安全策略允许。

### 6.5 BYOK 与托管模式

| 模式 | 上游 Key | 本模块控制 | 说明 |
|------|----------|------------|------|
| BYOK | 开发者自有 | 不控制上游成本 | AAF 只提供配置、路由与审计能力 |
| AAF 托管自用 | 官方 Gateway | 控制授权与总池 | 不允许再分销 |
| AAF 托管子代理 | 官方 Gateway | 控制授权、层级与总池 | 仅授权开发者可开通 |

源码交付无法阻止开发者改代码直连其他供应商。可控边界只是官方托管资源：只有通过官方 Gateway 验证的 developer key、license、proxy chain 与额度才能消耗官方资源。

### 6.6 数据模型

`developer_account`（账户与授权状态）、`developer_subscription_plan`、`developer_subscription`、`developer_token_account`（总 Token 池）、`developer_token_transaction`（流水）、`developer_redeem_code`、`developer_api_key`（Gateway 调用 Key）、`developer_proxy`（子代理关系）。

开发者管理表放 `v2__ai_schema.sql`，因为它服务于 AI 托管能力与模型网关资格；字典放 `v8__init_dict_data.sql`，演示套餐放 `v9__init_seed_data.sql`。

### 6.7 开发者自助接口

```text
GET  /api/developer/account/current
GET  /api/developer/subscription/plans
POST /api/developer/subscription/subscribe
GET  /api/developer/subscription/current
GET  /api/developer/tokens/account
GET  /api/developer/tokens/transactions
POST /api/developer/tokens/redeem
POST /api/developer/api-keys
GET  /api/developer/api-keys
POST /api/developer/proxies
GET  /api/developer/proxies
```

运营侧接口、权限码与管理员代开流程见 4.1 的 `developer` 模块门控说明。

### 6.8 Gateway 预留流程

```text
验证 developer_api_key
→ 校验 developer_account 授权
→ 校验 proxy chain 与 max_proxy_depth
→ 开发者 Token 池预检
→ 最终用户积分或权益预检
→ 调用模型
→ 写 ai_token_usage 与 gateway audit
→ 扣开发者 Token 池
→ 扣最终用户积分
```

真实扣费证明由官方服务返回，避免本地源码被修改后伪造用量或绕过扣费。

### 6.9 License 与最终安全边界

本地 License 只控制功能入口：是否显示 AAF 托管模型配置、是否显示子代理管理、是否允许创建 `developer_api_key`、是否允许选择托管模型来源。

**License 不是最终安全边界。** 最终边界由官方 Gateway 服务端校验 developer key、license fingerprint、proxy chain、nonce、timestamp、签名与额度。

### 6.10 后续任务

- 接入开发者订阅支付，替换当前直接开通的演示流程
- 增加开发者管理后台与运营审计视图
- 引入 License Runtime，把 `allow_managed_gateway` 与 `allow_sub_proxy` 绑定到签名 License
- 合规确认后再启用 `module/ai/gateway` 执行入口

## 7. 相关文档

- [需求规格](../../../task/v0.1.0/AAF-018/requirement.md) — 用户故事、验收标准、技术约束
- [访问控制设计](access-control.md) — 认证、授权（正交维度）
- [安全架构设计](security.md) — 加密、审计（正交维度）

## 8. 决策记录

| 日期 | 决策点 | 结论 | 理由 |
|------|--------|------|------|
| 2026-05-06 | 文档分工 | Design 聚焦实现，PRD 聚焦需求 | 避免重复，单一真理源 |
| | JWT 签名算法 | 待定（倾向 RS256） | |
| | Claims 字段 | 待定 | |
| | 公钥存放 | 待定（倾向多处冗余） | |
| | 耦合点选择 | 待定 | |
