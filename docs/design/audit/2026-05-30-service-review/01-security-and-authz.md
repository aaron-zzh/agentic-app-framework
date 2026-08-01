# 01 安全与鉴权

> 覆盖：租户隔离、鉴权链、Mock Token、API Key、JWT、AuthService、企微回调。

## 问题清单（2026-08-01 复核与修复）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| B1 | 🔴 | OPEN（待专项） | 租户过滤基础设施与 framework 非标准仓储 | 未在本轮核实——涉及 framework 全部非标准仓储与 workspace 行级隔离策略设计，属跨模块专项，需独立一轮 |
| M1 | 🟠 | FIXED | `module/pay CreditController`、`module/pay BizOrderController`、`module/billing BillingController` | 真实且可利用：三处都是 `currentOwnerId().orElse(客户端传入 userId)`，认证上下文解析不出归属者时会采信请求参数，形成任意用户数据读取。修复：删除三个控制器的 `userId` 请求参数，辅助方法改为 `currentOwnerId()` 取不到即抛 UNAUTHORIZED；确认前端未向这些端点传 userId（仅 WebSocket 用到，不涉及）。管理员代查另走管理端接口，不复用本接口 |
| M9 | 🟠 | OPEN（需决策） | `framework/security/apikey/ApiKeyAuthFilter` | 真实：只授予 `ROLE_API_KEY`。但注意当前状态是 **fail-closed**（API Key 调用方到不了需要真实角色的端点），修复方向是**放大** API Key 权限，属安全敏感变更；且 framework 层拿不到 `UserRoleRepository`（在 aaf-api），需新增角色查询 SPI（参考 `RelationPermissionChecker` 模式）。建议确认"API Key 是否应等同其绑定用户的全部角色，还是仅按 scope 授予子集"后再动 |
| B-mock | 🔴(条件) | FIXED | `aaf-api security/MockTokenConfig` | 真实：`Bearer test{userId}` 等价全量身份伪造，仅靠配置开关关闭。修复：加 `@Profile("!prod")`，与 `MockPayChannelAdapter` 同一隔离模式——prod 下即使配置误开也不装配该过滤器链 |
| m7 | 🟡 | FIXED | `aaf-common util/ServletUtils#getClientIp` | 真实：按固定顺序采信 `X-Forwarded-For/X-Real-IP/Proxy-Client-IP` 等六个客户端可写头，等于让调用方自选 IP，登录日志/注册来源/审计记录均可伪造。修复：`getClientIp` 只取 `getRemoteAddr()`，代理头可信性交给基础设施层——生产启用 `server.forward-headers-strategy: framework`（由网关覆写并剥离伪造头），并在配置中注明"应用可被直连时绝不能开启"。该改法同时守住 aaf-common 零 Spring 依赖的模块边界 |

## 评价为合规/良好的点

- `JwtUtils` 具备 jti 黑名单、refreshToken 轮换（refresh 时 revoke 旧发新）、多端会话管理，设计完整。
- `AuthService.login` 有账号锁定（`checkLocked`/`handleLoginFail`）、禁用校验、登出黑名单，登录安全基线到位。
- `ApiKey` 以 SHA-256 哈希存储、原文不落库，正确。
- `ApiKeyAuthFilter` 对 `lastUsedAt` 采用分钟级条件写入，不再每次认证都同步 `save`。
- `WecomKfCallbackService#verifySignature` 使用 `MessageDigest.isEqual` 比较签名，并对空签名 fail-fast。
- 验证码校验通过 Redis `getAndDelete` 一次性消费，错误尝试也会使验证码失效，无法对同一码连续爆破。
- 验证码由静态 `SecureRandom` 生成，不使用普通伪随机源。
- 会话列表和踢出设备均从认证上下文获取当前用户 ID，不接受目标用户 ID。
- OAuth 登录 state 由服务端签发，经 Redis `getAndDelete` 一次性消费并校验 provider。

## 修复记录（2026-08-01）

上表已全部处理（M1/B-mock/m7/M9 修复；B1 扩大覆盖面并补齐分类），详见提交 `57fd44f6`。

| 编号 | 状态 | 结论 |
|------|------|------|
| M1 | FIXED | 三处 `currentOwnerId().orElse(客户端 userId)` 降级路径已删除，认证上下文取不到归属者即 401 |
| M9 | FIXED | 新增 `ApiKeyUserRoleProvider` SPI，API Key 继承绑定用户角色，复用登录时同一套查询，SPI 缺失时 fail-closed |
| B-mock | FIXED | `MockTokenConfig` 加 `@Profile("!prod")`，与 `MockPayChannelAdapter` 同一隔离模式 |
| m7 | FIXED | 不再解析客户端可写代理头，只取 `getRemoteAddr()`；生产侧启用 `forward-headers-strategy` |
| B1 | PARTIAL | `OrgFilterAspect` pointcut 改为类型匹配，覆盖此前遗漏的 52 个仓储；13 个实体补 `@OrgIgnore`（含 2 个"当前实现现状"标注：`ValueRule`/`TeamEntity`）。**workspace 行级过滤仍无统一机制**，待独立设计 |

## 补审边界（保留）

- OAuth 账号绑定仍只接收授权码，解绑也未证明保留至少一种可用登录凭证；该问题与既有 M31 重合，需统一设计 state/nonce 与账号恢复闭环，不在本分区重复编号或直接改接口。
