/**
 * 特性演示：Route Group Layout + View Transitions API
 *
 * 1. Route Group：(dev)/examples/nextjs-features 不影响 URL 路径
 * 2. ViewTransitions：包裹整个示例，启用 CSS View Transitions API
 *    - 使用 next-view-transitions 库在 App Router 中支持原生过渡动画
 *    - 替代 layout 中的 <html><body>，适配 Route Group 嵌套布局场景
 */
import type { Metadata } from "next"
import { ViewTransitions } from "next-view-transitions"

export const metadata: Metadata = {
  title: "Next.js 特性演示 | AAF",
  description:
    "演示 Next.js App Router 核心特性：Route Groups、View Transitions、Streaming/Suspense、Server Actions"
}

export default function NextjsFeaturesLayout({ children }: { children: React.ReactNode }) {
  return (
    // ViewTransitions 包裹子树，启用 next-view-transitions 魔法
    // 内部 Link 组件触发页面切换时自动应用 CSS View Transition 动画
    <ViewTransitions>
      <div className="antialiased">{children}</div>
    </ViewTransitions>
  )
}
