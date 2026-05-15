/**
 * 应用级错误边界（Layer 1）——整个应用崩溃时的兜底页面
 * @author AaronZZH & Kiro
 */

"use client"

export default function GlobalError({
  error,
  reset
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-bold">出错了</h1>
      <p className="text-sm text-muted-foreground">{error.message || "发生了未知错误"}</p>
      <button
        type="button"
        onClick={reset}
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
      >
        重试
      </button>
    </div>
  )
}
