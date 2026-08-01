# 01 安全与鉴权

> 覆盖：租户隔离、鉴权链、Mock Token、API Key、JWT、AuthService、企微回调。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B1 | 🔴 | 租户过滤基础设施与 framework 非标准仓储 | framework 非标准仓储尚未统一纳入租户过滤，workspace 维度也缺少统一行级过滤 | 将 framework 非标准仓储纳入租户过滤；补齐 workspace 行级隔离策略 |
| M1 | 🟠 | `module/pay CreditController` 等 | 接口仍暴露 `@RequestParam Long userId`，并在认证上下文缺失时作为 fallback，保留危险签名与降级路径 | 删除当前用户接口的 userId 参数与 fallback；管理员代查另设鉴权接口 |
| M9 | 🟠 | `framework/security/apikey/ApiKeyAuthFilter` | API Key 认证仍只授予 `ROLE_API_KEY`，未继承关联用户的真实角色 | 认证后加载并注入关联用户角色，同时保留 scope 与 allowedTables 的收窄约束 |
| B-mock | 🔴(条件) | `aaf-api security/MockTokenConfig`+`MockTokenFilter` | `Bearer test{userId}` 全量身份伪造，仅靠 `mock-enable=true` 关闭，密钥硬编码"test"，无环境隔离 | 加 `@Profile("!prod")` 双保险；生产构建剔除 |
| m7 | 🟡 | `aaf-common util/ServletUtils#getClientIp` | 盲信 `X-Forwarded-For/X-Real-IP`，可伪造 | 仅在可信代理后取首段，或由网关注入可信头 |

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

## 补审边界

- OAuth 账号绑定仍只接收授权码，解绑也未证明保留至少一种可用登录凭证；该问题与既有 M31 重合，需统一设计 state/nonce 与账号恢复闭环，不在本分区重复编号或直接改接口。
