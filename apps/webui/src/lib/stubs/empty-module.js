/**
 * proxy-agent 系列空 stub
 * ──────────────────────────────────────────────────────────────────
 *
 * 为何存在：
 *   ali-oss@6 → urllib → detect_proxy_agent.js 里有 `new (require('proxy-agent'))(proxy)`，
 *   proxy-agent 不在依赖中（urllib 把它当 lazy optional），bundler 编译期解析不到就报：
 *     Module not found: Can't resolve 'proxy-agent'
 *
 *   运行时其实永远走不到那一行——urllib 里上面写了 `if (!proxy) return null`，
 *   浏览器/SSR 没 HTTP_PROXY 环境变量，整个函数提前 return。
 *   所以只需给编译期一个能解析到的"占位模块"，运行时根本不会被调用。
 *
 * 在哪里被引用：
 *   apps/webui/next.config.ts
 *     - turbopack.resolveAlias（dev 模式 next dev --turbopack 用）
 *     - webpack.resolve.alias（next build 生产构建用）
 *   把 proxy-agent / https-proxy-agent / http-proxy-agent / socks-proxy-agent /
 *   pac-proxy-agent 这一族全部映射到本文件。
 *
 * 为何写成可被 new 的 Proxy 而不是单纯导出 {}：
 *   万一未来运行时真的触达（比如有人在 SSR 阶段意外塞了代理 env），
 *   `new ProxyAgent(...)` 不会立刻 ReferenceError，任意属性访问也不会炸。
 *   纯防御性写法，成本极低。
 */

class EmptyAgent {}

const handler = {
  get() {
    return () => undefined
  }
}

const empty = new Proxy(EmptyAgent, handler)

module.exports = empty
module.exports.default = empty
