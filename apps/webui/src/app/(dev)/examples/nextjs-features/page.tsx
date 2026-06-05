/**
 * 特性演示：View Transitions + useTransitionRouter 自定义动画
 *
 * 1. Link（来自 next-view-transitions）：触发默认淡入淡出过渡
 * 2. useTransitionRouter：手动触发路由跳转，并注入自定义 Web Animations API 动画
 * 3. view-transition-name（CSS）：为特定元素设置名称，实现"共享元素过渡"
 */
"use client"

import { ArrowRight as ArrowRightIcon } from "lucide-react"
import { Link, useTransitionRouter } from "next-view-transitions"
import { Button } from "@/components/ui/button"

/** 自定义过渡动画：旧页面左滑出，新页面右滑入 */
function slideInOut() {
  document.documentElement.animate(
    [
      { opacity: 1, transform: "translate(0, 0)" },
      { opacity: 0, transform: "translate(-80px, 0)" }
    ],
    {
      duration: 350,
      easing: "ease",
      fill: "forwards",
      pseudoElement: "::view-transition-old(root)"
    }
  )
  document.documentElement.animate(
    [
      { opacity: 0, transform: "translate(80px, 0)" },
      { opacity: 1, transform: "translate(0, 0)" }
    ],
    {
      duration: 350,
      easing: "ease",
      fill: "forwards",
      pseudoElement: "::view-transition-new(root)"
    }
  )
}

export default function NextjsFeaturesHome() {
  const router = useTransitionRouter()

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 p-8">
      <div className="mx-auto max-w-2xl">
        {/* 标题设置了 view-transition-name，实现跨页面共享元素过渡 */}
        <h1
          className="mb-2 font-bold text-4xl text-slate-800"
          style={{ viewTransitionName: "page-title" }}
        >
          Next.js 特性演示
        </h1>
        <p className="mb-8 text-slate-500">展示 App Router 核心能力的交互示例</p>

        <div className="space-y-4">
          {/* 特性 1：Dashboard - Streaming/Suspense/Server Components */}
          <FeatureCard
            title="Dashboard — Streaming & Suspense"
            desc="Server Components + Suspense 流式渲染，骨架屏占位，loading.tsx 自动包裹"
            href="/examples/nextjs-features/dashboard"
          />

          {/* 特性 2：发票列表 - URL 搜索参数 + 防抖 */}
          <FeatureCard
            title="发票列表 — URL 搜索参数 + 防抖"
            desc="useSearchParams 驱动搜索状态，useDebouncedCallback 防止频繁请求，分页 URL 参数"
            href="/examples/nextjs-features/dashboard/invoices"
          />

          {/* 特性 3：View Transitions 自定义动画 */}
          <div className="rounded-xl border border-blue-200 bg-white p-5 shadow-sm">
            <div className="mb-1 flex items-center gap-2">
              <span className="rounded bg-blue-100 px-2 py-0.5 font-medium text-blue-700 text-xs">
                View Transitions
              </span>
            </div>
            <h2 className="mb-1 font-semibold text-slate-700">自定义过渡动画演示</h2>
            <p className="mb-3 text-slate-500 text-sm">
              使用 useTransitionRouter + Web Animations API 实现左滑过渡效果
            </p>
            <Button
              onClick={() =>
                router.push("/examples/nextjs-features/dashboard", {
                  onTransitionReady: slideInOut
                })
              }
            >
              自定义动画跳转到 Dashboard <ArrowRightIcon className="h-4 w-4" />
            </Button>
          </div>

          {/* 特性 4：View Transitions 说明 */}
          <FeatureCard
            title="View Transitions 使用说明"
            desc="next-view-transitions 库文档示例，包含安装和使用方式"
            href="/examples/nextjs-features/view"
          />
        </div>

        <div className="mt-8 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-800 text-sm">
          <strong>注意：</strong>此示例使用 Mock 数据，无需数据库。重点在于展示 Next.js
          路由、数据获取和渲染模式。
        </div>
      </div>
    </div>
  )
}

function FeatureCard({ title, desc, href }: { title: string; desc: string; href: string }) {
  return (
    <Link
      href={href}
      className="block rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
    >
      <h2 className="mb-1 font-semibold text-slate-700">{title}</h2>
      <p className="text-slate-500 text-sm">{desc}</p>
    </Link>
  )
}
