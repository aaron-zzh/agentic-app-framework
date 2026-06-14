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

const nextConfig: NextConfig = {
  allowedDevOrigins: ["192.168.0.*"], // 其他设备访问测试
  output: "standalone",
  webpack: (config) => {
    config.resolve.alias.canvas = false
    return config
  },
  images: {
    loader: "custom",
    loaderFile: "./src/lib/utils/image-loader.ts",
    formats: ["image/avif", "image/webp"],
    remotePatterns: [
      { protocol: "https", hostname: "**.aliyuncs.com" },
      { protocol: "https", hostname: "**.minio.io" },
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
