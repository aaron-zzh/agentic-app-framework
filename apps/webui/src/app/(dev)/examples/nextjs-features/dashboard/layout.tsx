/**
 * 特性演示：嵌套 Layout
 *
 * Dashboard 有自己的 layout，与外层 nextjs-features/layout.tsx 构成嵌套布局：
 * - 外层：提供 ViewTransitions 容器
 * - 本层：提供侧边导航 + 内容区域的二列布局
 *
 * PPR（Partial Prerendering）说明：
 * Next.js 16 默认启用，导航、侧边栏等静态部分在构建时生成，
 * 动态内容（Suspense 包裹部分）在请求时流式传输，无需额外配置。
 */
import type { Metadata } from "next"

import SideNav from "../_components/dashboard/sidenav"

export const metadata: Metadata = {
  title: {
    template: "%s | Next.js 特性演示",
    default: "Dashboard"
  }
}

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen flex-col md:flex-row md:overflow-hidden">
      {/* 侧边导航：静态部分，PPR 中在构建时预渲染 */}
      <div className="w-full flex-none md:w-40">
        <SideNav />
      </div>
      {/* 动态内容区：Suspense 包裹的流式内容在此渲染 */}
      <div className="grow p-4 md:overflow-y-auto md:p-6">{children}</div>
    </div>
  )
}
