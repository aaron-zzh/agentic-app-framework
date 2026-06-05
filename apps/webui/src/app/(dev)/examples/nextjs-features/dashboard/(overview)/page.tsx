/**
 * 特性演示：Streaming + Suspense 细粒度流式渲染
 *
 * 数据获取策略：
 * - latestInvoices：页面级 await（阻塞整个页面，演示对比）
 * - CardWrapper：组件内部 async fetch，Suspense 包裹（延时 1s，骨架屏替换）
 * - RevenueChart：组件内部 async fetch，Suspense 包裹（延时 3s，演示长等待骨架屏）
 *
 * loading.tsx 与 Suspense 的区别：
 * - loading.tsx：自动为整个路由创建 Suspense，粒度是"整页"
 * - <Suspense>：手动细粒度控制，可以让页面其他内容先显示
 */

import type { Metadata } from "next"
import { Suspense } from "react"
import CardWrapper from "../../_components/dashboard/cards"
import LatestInvoices from "../../_components/dashboard/latest-invoices"
import RevenueChart from "../../_components/dashboard/revenue-chart"
import { CardsSkeleton, RevenueChartSkeleton } from "../../_components/skeletons"
import { fetchLatestInvoices } from "../../_data/mock"

export const metadata: Metadata = { title: "Dashboard" }

export default async function DashboardPage() {
  // 页面级数据获取——阻塞页面，但不影响 Suspense 包裹的组件
  const latestInvoices = await fetchLatestInvoices()

  return (
    <main>
      <h1 className="mb-4 font-bold text-slate-800 text-xl md:text-2xl">Dashboard</h1>

      {/* 卡片区域：Suspense 包裹，1秒延时后替换骨架屏 */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Suspense fallback={<CardsSkeleton />}>
          <CardWrapper />
        </Suspense>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-4 lg:grid-cols-8">
        {/* 收入图表：Suspense 包裹，3秒延时，演示长时间骨架屏 */}
        <Suspense fallback={<RevenueChartSkeleton />}>
          <RevenueChart />
        </Suspense>

        {/* 最新发票：页面级数据，立即渲染 */}
        <LatestInvoices latestInvoices={latestInvoices} />
      </div>
    </main>
  )
}
