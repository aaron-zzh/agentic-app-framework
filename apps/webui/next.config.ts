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
// Turbopack resolveAlias 必须用相对路径（相对于 next.config.ts 所在目录）：
//   - 绝对路径在 Docker 容器内被识别为 "server relative import"，Turbopack 尚不支持 → 报错
//   - Next.js 16 `next build` 默认走 Turbopack，两套配置都必须生效
// webpack alias 用 path.resolve 绝对路径（webpack 支持，本地和 CI 都正常）。
const emptyModuleAbsolute = path.resolve(__dirname, "src/lib/stubs/empty-module.js")
const emptyModuleRelative = "./src/lib/stubs/empty-module.js"
const proxyAgentPackages = ["proxy-agent", "https-proxy-agent", "http-proxy-agent", "socks-proxy-agent", "pac-proxy-agent"]
const proxyAgentStubsAbsolute = Object.fromEntries(proxyAgentPackages.map((p) => [p, emptyModuleAbsolute]))
const proxyAgentStubsRelative = Object.fromEntries(proxyAgentPackages.map((p) => [p, emptyModuleRelative]))

const nextConfig: NextConfig = {
  allowedDevOrigins: ["192.168.0.*"], // 其他设备访问测试
  output: "standalone",
  // Turbopack（next dev --turbopack）配置入口
  // ⚠️ 关键点：Next.js 16 dev 默认走 Turbopack，下面的 webpack 配置在 dev 下完全不生效，
  // 必须用 turbopack.resolveAlias 才能在 dev 模式下命中 stub。
  turbopack: {
    // Turbopack resolveAlias 必须用相对路径，绝对路径会被识别为 server-relative import → 报错
    resolveAlias: proxyAgentStubsRelative
  },
  // webpack 配置：生产构建降级到 webpack 时仍然生效（用绝对路径，webpack 支持）
  webpack: (config) => {
    config.resolve.alias.canvas = false
    // ⚠️ 不能加 `if (!isServer)` 守卫——报错 trace 第一行是 [Client Component SSR]，
    // 这是 server 端预渲染 Client Component 的阶段，走的是 server 解析路径，
    // 必须 server/client 两端都打 stub。
    config.resolve.alias = {
      ...config.resolve.alias,
      ...proxyAgentStubsAbsolute
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
