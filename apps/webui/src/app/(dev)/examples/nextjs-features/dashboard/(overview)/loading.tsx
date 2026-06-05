/**
 * 特性演示：loading.tsx 路由级加载状态
 *
 * Next.js 自动将此文件包裹在 <Suspense> 中，作为整个路由的 fallback。
 * 导航到此路由时立即显示骨架屏，无需等待数据加载完成。
 * 与组件级 <Suspense> 的区别：粒度是整个页面 vs 单个组件。
 */
import DashboardSkeleton from "../../_components/skeletons"

export default function Loading() {
  return <DashboardSkeleton />
}
