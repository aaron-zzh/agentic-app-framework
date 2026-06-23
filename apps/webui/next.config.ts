import path from "node:path"
import { composePlugins, withNx } from "@nx/next"
import withSerwistInit from "@serwist/next"
import type { NextConfig } from "next"
import createNextIntlPlugin from "next-intl/plugin"

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts")

const withSerwist = withSerwistInit({
  swSrc: "src/app/sw.ts",
  swDest: "public/sw.js",
  disable: process.env.NODE_ENV !== "production"
})

// ─── proxy-agent 系列 stub ───────────────────────────────────────────────
//
// 问题链路：
//   ali-oss@6 (Node SDK)
//     → 内部 require('urllib')
//       → urllib/lib/detect_proxy_agent.js 第 24 行：new (require('proxy-agent'))(proxy)
//
// 为什么报错：
//   1. proxy-agent 不在 package.json，是 urllib 的可选 lazy 依赖；
//   2. require('proxy-agent') 是字面量字符串，bundler 编译期必须解析它，解析不到 → Module not found；
//   3. 运行时其实根本走不到这一行——上面有 `if (!proxy) return null`，
//      浏览器/SSR 没 HTTP_PROXY 环境变量，proxy 永远是空，整个函数提前 return。
//
// 解决：
//   把 proxy-agent 这一族模块全部 alias 到一个空 stub（src/lib/stubs/empty-module.js）。
//   编译期能解析 ✅，运行时永远不调用 ✅，stub 自身可被 new 调用作为兜底 ✅。
//
// 为什么需要正斜杠：
//   Windows 下 Turbopack 不接受反斜杠绝对路径（报 "windows imports are not implemented yet"），
//   path.resolve 在 Windows 给的是 `D:\code\...`，必须 .replace(/\\/g, "/") 转成 `D:/code/...`。
const emptyModule = path.resolve(__dirname, "src/lib/stubs/empty-module.js").replace(/\\/g, "/")
const proxyAgentStubs = {
  "proxy-agent": emptyModule,
  "https-proxy-agent": emptyModule,
  "http-proxy-agent": emptyModule,
  "socks-proxy-agent": emptyModule,
  "pac-proxy-agent": emptyModule
}

const nextConfig: NextConfig = {
  allowedDevOrigins: ["192.168.0.*"], // 其他设备访问测试
  output: "standalone",
  // Turbopack（next dev --turbopack）配置入口
  // ⚠️ 关键点：Next.js 16 dev 默认走 Turbopack，下面的 webpack 配置在 dev 下完全不生效，
  // 必须用 turbopack.resolveAlias 才能在 dev 模式下命中 stub。
  turbopack: {
    resolveAlias: proxyAgentStubs
  },
  // webpack 配置仅用于 next build（生产构建仍是 webpack）
  webpack: (config) => {
    config.resolve.alias.canvas = false
    // ⚠️ 不能加 `if (!isServer)` 守卫——报错 trace 第一行是 [Client Component SSR]，
    // 这是 server 端预渲染 Client Component 的阶段，走的是 server 解析路径，
    // 必须 server/client 两端都打 stub。
    config.resolve.alias = {
      ...config.resolve.alias,
      ...proxyAgentStubs
    }
    return config
  },
  images: {
    loader: "custom",
    loaderFile: "./src/lib/utils/image-loader.ts",
    formats: ["image/avif", "image/webp"],
    remotePatterns: [
      { protocol: "https", hostname: "**.aliyuncs.com" },
      { protocol: "https", hostname: "**.minio.io" },
      { protocol: "http", hostname: "localhost" },
      { protocol: "https", hostname: "cdn.aaronzzh.cn" },
      ...(process.env.NEXT_PUBLIC_ASSETS_URL
        ? [
            {
              protocol: "https" as const,
              hostname: new URL(process.env.NEXT_PUBLIC_ASSETS_URL).hostname
            }
          ]
        : [])
    ]
  },
  experimental: {
    optimizePackageImports: [
      "lucide-react",
      "date-fns",
      "es-toolkit",
      "@xyflow/react",
      "echarts",
      "framer-motion"
    ]
  },
  headers: async () => [
    {
      source: "/(.*)",
      headers: [
        { key: "X-DNS-Prefetch-Control", value: "on" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "X-Frame-Options", value: "SAMEORIGIN" },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains; preload" },
        {
          key: "Content-Security-Policy",
          value:
            "default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline' https://o.alicdn.com https://g.alicdn.com; style-src 'self' 'unsafe-inline' https://g.alicdn.com; img-src 'self' data: blob: http: https:; font-src 'self' data: https://g.alicdn.com; connect-src 'self' blob: http: https: ws: wss:; media-src 'self' blob: http: https:"
        }
      ]
    }
  ]
}

const plugins = [withNx, withNextIntl, withSerwist]

export default composePlugins(...plugins)(nextConfig)
