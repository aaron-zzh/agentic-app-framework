/**
 * 特性演示：error.tsx 路由级错误边界
 *
 * Next.js 自动将此文件包裹在 React Error Boundary 中。
 * 捕获路由内的运行时错误，防止整个应用崩溃。
 *
 * reset()：尝试重新渲染路由，不刷新页面（客户端恢复）
 * error.digest：服务端错误的哈希摘要，用于日志关联
 */
"use client"

import { useEffect } from "react"

export default function InvoicesError({
  error,
  reset
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  useEffect(() => {
    // 生产环境建议上报到错误监控服务（如 Sentry）
    console.error("Invoice page error:", error)
  }, [error])

  return (
    <main className="flex h-full flex-col items-center justify-center gap-4">
      <h2 className="font-semibold text-slate-700 text-xl">出错了！</h2>
      <p className="text-slate-500 text-sm">{error.message || "加载发票数据时发生错误"}</p>
      <button
        className="rounded-md bg-blue-500 px-4 py-2 text-sm text-white transition-colors hover:bg-blue-400"
        onClick={reset}
      >
        重试
      </button>
    </main>
  )
}
