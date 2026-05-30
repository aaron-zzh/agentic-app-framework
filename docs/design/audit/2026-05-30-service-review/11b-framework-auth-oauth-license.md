# 11b framework 认证 · OAuth · License（优先级 2）

> 覆盖：`framework/security/oauth/`（Wechat/Wecom/Dingtalk 客户端、Properties、AutoConfiguration）与 `framework/security/license/`（LicenseLoader、LicenseAspect、PluginRegistry、@PremiumRequired）。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 2。审查人 AI/architect · 2026-05-30。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M31 | 🟠 | `oauth/OAuthClient` 接口 + 三个客户端 | 抽象**无 state/nonce 校验原语**：`buildAuthorizationUrl(state)` 只拼 state，`exchangeToken(code)` 不接收也不校验 state。CSRF/登录态固定防护完全外包给调用方且无强制→易出现 OAuth 登录 CSRF/账号绑定劫持 | 接口增 `exchangeToken(code, state)`；框架提供 state 生成+存储+校验原语，回调强制校验 |
| M32 | 🟠 | `license/LicenseLoader#PUBLIC_KEY_PEM` | 硬编码"测试用"RSA 公钥（注释"生产环境替换"）。若生产未替换且配套测试私钥可得，可自签任意 `tier=premium` JWT→伪造许可证绕过营收门控 | 公钥经配置/环境注入，禁止硬编码；CI 校验生产 profile 未用测试公钥 |
| M33 | 🟠 | `license/PremiumRequired` + `LicenseAspect` | `@PremiumRequired` **零使用**（全仓无注解点）。切面可用但无方法施加→premium 门控形同虚设，高级功能实际未被授权拦截 | 在真实高级功能入口施加注解，或补充 ArchUnit/测试保证关键功能被门控 |
| m21 | 🟡 | `oauth/*OAuthClient#exchangeToken` | `log.debug("微信 token 响应: {}", tokenResp)` 等把 access_token/refresh_token/openid 明文写 DEBUG 日志→开启 DEBUG 时令牌泄漏（同 M11 思路） | 脱敏或移除令牌日志 |
| m22 | 🟡 | `oauth/*OAuthClient#exchangeToken` | 无 token 响应错误/null 校验：渠道返回 `{errcode,errmsg}` 时 `access_token` 为 null，仍继续调 userinfo→NPE / 用 null token 请求，错误被掩盖 | 校验响应成功字段，失败抛 BusinessException 并记录 errcode |
| m23 | 🟡 | `oauth/OAuthAutoConfiguration` | 未配置时从 `@Bean` 方法 `return null`（反模式，注入 NullBean / 下游 NPE 风险） | 改用 `@ConditionalOnProperty`，未配置则不注册 Bean |

## 良好实践

- License 采用 RSA 签名 JWT + 启动验签 + 过期校验，签名模型正确（私钥不泄漏则无法伪造）；异常一律降级免费模式（向低权限 fail-safe，licensing 场景合理）。
- OAuth 客户端按配置非空条件装配；`License` 单例 `volatile` 字段 + 仅启动 activate 一次，运行期只读。
- `PluginRegistry`/`LicenseAwareConfig` 统一以 `License.isPremium()` 派生能力，分级集中。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：OAuth 回调缺 state 校验（M31），与 B3/M5 webhook 验签缺失同源——回调类入口普遍缺来源校验。
- 成功路径 vs 错误路径（清单#9）：exchangeToken 只走成功路径，渠道错误响应无处理（m22）。
- 已有控制 vs 实际施加（清单#13）：`@PremiumRequired` 控制存在但零施加（M33），同 `@PremiumRequired` 与 license 体系脱节。

## 待确认

- 是否存在 OAuth 登录控制器/服务实际调用 `buildAuthorizationUrl`/`exchangeToken`（符号搜索未在 api 命中调用方，登录流可能尚未接线）——决定 M31 是当前真实暴露还是潜在缺口。
- 配套测试私钥是否在仓库/测试资源中可得（决定 M32 是否升级）。
