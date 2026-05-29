# 安全加固建议

执行者：AI/developer-webui
日期：2026-05-29

## 当前 middleware.ts 安全状态

### 已实现

- ✅ 路由守卫（未登录重定向登录页）
- ✅ 已登录用户跳过认证页
- ✅ Cookie-based token 检测

### 未实现

- ❌ 安全响应头（CSP/HSTS/X-Frame-Options）
- ❌ CSRF 防护
- ❌ Rate Limiting
- ❌ 请求来源校验

## 建议添加的安全头

在 `middleware.ts` 中添加安全响应头：

```typescript
// 在 NextResponse.next() 前设置安全头
const response = NextResponse.next()
const headers = response.headers

// 防止点击劫持
headers.set("X-Frame-Options", "SAMEORIGIN")

// 防止 MIME 类型嗅探
headers.set("X-Content-Type-Options", "nosniff")

// XSS 防护（现代浏览器已内置，作为兜底）
headers.set("X-XSS-Protection", "1; mode=block")

// HSTS（仅生产环境）
if (process.env.NODE_ENV === "production") {
  headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
}

// Referrer 策略
headers.set("Referrer-Policy", "strict-origin-when-cross-origin")

// 权限策略
headers.set("Permissions-Policy", "camera=(), microphone=(self), geolocation=()")

// CSP（内容安全策略）
headers.set("Content-Security-Policy", [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval'",  // Next.js 需要
  "style-src 'self' 'unsafe-inline'",                  // Tailwind 需要
  "img-src 'self' data: blob: https:",
  "font-src 'self'",
  "connect-src 'self' wss: https:",                    // WebSocket + API
  "frame-ancestors 'self'",
].join("; "))

return response
```

## 安全加固清单

### 高优先级（Beta 前必须）

| 项目 | 风险 | 建议 |
|------|------|------|
| HSTS | 中间人攻击 | 生产环境强制 HTTPS |
| CSP | XSS 注入 | 限制脚本/样式/连接来源 |
| httpOnly Cookie | Token 窃取 | 确认 aaf-token 设置了 httpOnly + Secure + SameSite |
| API 代理 SSRF | 服务端请求伪造 | `app/api/proxy/route.ts` 限制目标域名白名单 |
| 文件上传校验 | 恶意文件 | 服务端校验文件类型/大小，不信任 Content-Type |

### 中优先级

| 项目 | 风险 | 建议 |
|------|------|------|
| Rate Limiting | 暴力破解/DDoS | 登录接口限流（5次/分钟），API 全局限流 |
| CSRF Token | 跨站请求伪造 | Server Actions 已内置防护，REST API 需额外处理 |
| 敏感数据脱敏 | 信息泄露 | API 响应中手机号/邮箱部分遮蔽 |
| 错误信息 | 信息泄露 | 生产环境不暴露堆栈和内部错误详情 |

### 低优先级（v1.0+）

| 项目 | 建议 |
|------|------|
| Subresource Integrity | 第三方 CDN 资源添加 SRI hash |
| Report-To / NEL | 安全事件上报端点 |
| 依赖审计 | 定期 `pnpm audit`，CI 集成 |

## API 客户端安全检查

当前 `lib/api/client.ts` 状态：

- ✅ 统一 JWT 携带
- ✅ 错误码映射
- ⚠️ 建议：401 响应时自动清除 token 并重定向登录
- ⚠️ 建议：请求超时设置（默认 30s）
- ⚠️ 建议：敏感接口（支付/密码修改）增加二次确认
