# 01 安全与鉴权

> 覆盖：租户隔离、鉴权链、Mock Token、API Key、JWT、AccessControl 切面、AuthService、企微回调。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B1 | 🔴 | `aaf-api config/TenantFilter`+`TenantFilterAspect`+`TenantContext` | 租户 orgId 取自请求头 `X-Org-Id`，无归属校验→任意用户越权访问他组织数据；缺省 orgId=null 时不启用过滤器→返回全部数据（fail-open）；framework 层 repository 不受过滤；workspace 维度未隔离 | orgId 从已认证身份推导并校验归属；无上下文 fail-closed；过滤覆盖所有 repository |
| M1 | 🟠 | `module/pay CreditController` 等 | 控制器以 `@RequestParam Long userId` 作身份入参→IDOR；`OperatorContext` 已能从 JWT 取当前用户却被绕过 | "当前用户"语义一律取 `OperatorContext`；全量 grep `@RequestParam Long userId` 排查 |
| M9 | 🟠 | `framework/security/apikey/ApiKeyAuthFilter` + `ApiKey` | Key 声称"继承用户权限 + scope/allowedTables 限制"，但过滤器只授予 `ROLE_API_KEY`，未注入用户真实角色、未在任何拦截点强制 `hasScope/canAccessTable` | 认证后加载用户角色；在数据访问层强制 scope 与 allowedTables |
| M10 | 🟠 | `module/customerservice WecomKfCallbackController` vs `SecurityConfig.PUBLIC_PATHS` | `/api/wecom/kf/callback` 不在白名单→企微服务器无法带 JWT 回调（打不通）；服务自身已做验签，本应公开 | 将回调路径加入白名单（验签即认证） |
| M11 | 🟠 | `module/system/auth AuthService#sendCode` | 验证码以 INFO 明文写日志：`【验证码】...验证码={}`→日志泄露 | 降为本地 dev debug 或移除，生产禁止打印验证码 |
| M8 | 🟠 | `framework/security SecurityConfig#jwtSecretKey` | `secret().getBytes()` 用平台默认字符集；HS256 要求≥256bit，无长度校验 | 显式 UTF-8；启动期校验密钥长度 |
| B-mock | 🔴(条件) | `aaf-api security/MockTokenConfig`+`MockTokenFilter` | `Bearer test{userId}` 全量身份伪造，仅靠 `mock-enable=true` 关闭，密钥硬编码"test"，无环境隔离 | 加 `@Profile("!prod")` 双保险；生产构建剔除 |
| m7 | 🟡 | `aaf-common util/ServletUtils#getClientIp` | 盲信 `X-Forwarded-For/X-Real-IP`，可伪造 | 仅在可信代理后取首段，或由网关注入可信头 |
| m8 | 🟡 | `ApiKeyAuthFilter` | 注释称"异步更新最后使用时间"，实为每请求同步 `save`→热点写 | 改异步/节流（如分钟级合并） |
| m10 | 🟡 | `WecomKfCallbackService#verifySignature`、`WebhookService` | 签名比较用 `String.equals`，非常量时间 | 用 `MessageDigest.isEqual` |

## 评价为合规/良好的点

- `JwtUtils` 具备 jti 黑名单、refreshToken 轮换（refresh 时 revoke 旧发新）、多端会话管理，设计完整。
- `AuthService.login` 有账号锁定（`checkLocked`/`handleLoginFail`）、禁用校验、登出黑名单，登录安全基线到位。
- `AccessControlAspect`（Layer1）角色 + 功能开关校验逻辑正确。
- `ApiKey` 以 SHA-256 哈希存储、原文不落库，正确。

## 待确认（未读全）

- `AuthService#validateCode`：是否一次性失效验证码 + 限制尝试次数（防 6 位码爆破）。
- `generateCode()`：是否使用 `SecureRandom`（当前其他处可见 `ThreadLocalRandom`，若用于验证码则强度不足）。
- AuthService 250 行后（OAuth 绑定、改密、会话列表）未读，建议补审。
