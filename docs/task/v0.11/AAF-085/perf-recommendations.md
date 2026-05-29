# 性能优化建议

执行者：AI/developer-webui
日期：2026-05-29

## next.config.ts 优化配置

当前 `next.config.ts` 的 `nextConfig` 为空对象，建议添加以下配置：

```typescript
const nextConfig: NextConfig = {
  // 图片优化
  images: {
    formats: ["image/avif", "image/webp"],
    remotePatterns: [
      { protocol: "https", hostname: "**.aliyuncs.com" },
      { protocol: "https", hostname: "**.minio.io" },
    ],
  },
  // 实验性优化
  experimental: {
    optimizePackageImports: [
      "lucide-react",
      "date-fns",
      "es-toolkit",
      "@xyflow/react",
      "echarts",
      "framer-motion",
    ],
  },
  // 安全头
  headers: async () => [
    {
      source: "/(.*)",
      headers: [
        { key: "X-DNS-Prefetch-Control", value: "on" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      ],
    },
  ],
};
```

## 优化建议清单

### 高优先级

| 项目 | 当前状态 | 建议 | 预期收益 |
|------|---------|------|---------|
| 包导入优化 | 未配置 `optimizePackageImports` | 添加大型库到优化列表 | 减少 bundle 30-50% |
| 图片格式 | 未配置 AVIF | 启用 AVIF + WebP | 图片体积减少 40-60% |
| 动态导入 | 部分重组件未 lazy load | ECharts/XYFlow/Lexical 用 `dynamic()` | 首屏 JS 减少 200KB+ |
| React Compiler | 未启用 | Next.js 16 内置，零配置 | 自动 memo 优化 |

### 中优先级

| 项目 | 当前状态 | 建议 | 预期收益 |
|------|---------|------|---------|
| 虚拟滚动 | 部分列表未使用 | 数据量 > 50 行启用 @tanstack/react-virtual | 长列表渲染 60fps |
| Prefetch 策略 | 默认全量预取 | 视口内链接预取，非关键路由延迟 | 减少不必要网络请求 |
| Service Worker | 已配置 Serwist | 确认静态资源缓存策略正确 | 二次访问秒开 |
| TanStack Query staleTime | 默认 0 | 实体列表设 30s，配置类设 5min | 减少重复请求 |

### 低优先级（v1.0+）

| 项目 | 建议 |
|------|------|
| PPR（Partial Prerendering） | 静态壳 + 动态流式注入，TTFB 降低 60-80% |
| Edge Runtime | 中间件和轻量 API 路由迁移到 Edge |
| Bundle Analyzer | 定期分析 bundle 组成，识别冗余依赖 |

## 关键指标目标

| 指标 | 当前（估计） | 目标 |
|------|------------|------|
| LCP | ~2.5s | < 1.5s |
| FID | ~100ms | < 50ms |
| CLS | ~0.1 | < 0.05 |
| JS Bundle (首屏) | ~500KB | < 300KB |
