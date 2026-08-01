# 01 安全与鉴权

> 覆盖：租户隔离、鉴权链、Mock Token、API Key、JWT、AuthService、企微回调。

## 问题清单（2026-08-01 复核与修复）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| B1 | 🔴 | FIXED | 租户过滤基础设施与 framework 非标准仓储 | `OrgFilterAspect` pointcut 扩大覆盖面；workspace 维度复核确认已有独立设计（[workspace-isolation.md](../../apps/service/workspace-isolation.md)）并落地，非遗留问题，详见下方修复记录 |
| M1 | 🟠 | FIXED | `module/pay CreditController`、`module/pay BizOrderController`、`module/billing BillingController` | 真实且可利用：三处都是 `currentOwnerId().orElse(客户端传入 userId)`，认证上下文解析不出归属者时会采信请求参数，形成任意用户数据读取。修复：删除三个控制器的 `userId` 请求参数，辅助方法改为 `currentOwnerId()` 取不到即抛 UNAUTHORIZED；确认前端未向这些端点传 userId（仅 WebSocket 用到，不涉及）。管理员代查另走管理端接口，不复用本接口 |
| M9 | 🟠 | FIXED | `framework/security/apikey/ApiKeyAuthFilter` | 真实：只授予 `ROLE_API_KEY`。按简洁方式修复：新增 `ApiKeyUserRoleProvider` SPI（framework），`ApiKeyUserRoleAdapter`（aaf-api）实现并复用登录时同一套角色查询，`ApiKeyAuthFilter` 注入角色，SPI 缺失或异常时 fail-closed 回退仅 `ROLE_API_KEY` |
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
| B1 | FIXED | `OrgFilterAspect` pointcut 改为类型匹配，覆盖此前遗漏的 52 个仓储；13 个实体补 `@OrgIgnore`（含 2 个"当前实现现状"标注：`ValueRule`/`TeamEntity`）。**workspace 维度复核（2026-08-01）**：曾以为"完全无机制"，实际已有独立设计并落地——[workspace-isolation.md](../../apps/service/workspace-isolation.md) 采用显式 `Specification` 拼接（`BaseCrudService#workspaceSpec()`），而非复用 org 的 Hibernate Filter 方案（该方案已知有三类缺陷，文档记录在案）。`workspace_id=NULL` 表示"组织级共享"是刻意的产品语义，不是隔离缺失；`TenantScope.WORKSPACE_REQUIRED` 全代码库零使用，是因为目前没有资源需要强制工作区边界，非漏配。约 37 个文件手动处理 `workspaceId` 是文档记录的已知取舍（"未经过 `BaseCrudService` 的路径需业务代码显式加条件"），不是遗漏 |

## 补审边界（保留）

- OAuth 解绑未保证保留至少一种可用登录凭证：`AuthService#unbindOAuth` 直接删除绑定，不检查用户是否还有密码或其他登录方式；且 OAuth 注册用户的密码是 `randomPassword()` 生成的随机值（用户不知道），实质等于"无密码"。若用户仅绑定单个 OAuth 且未设置过密码，解绑后账号永久锁死。**核实结论：与 M31 不是同一问题**——M31 是绑定流程缺 state 导致账号劫持（已修复），这是解绑流程缺兜底导致账号锁死（未修复），审计原文"重合"判断不准确。本次未处理，需要产品决定兜底策略（解绑前强制设置密码 / 保留最后一种登录方式不可解绑 / 允许账号锁死后走找回流程）后再改代码，不在本轮任务范围内。
