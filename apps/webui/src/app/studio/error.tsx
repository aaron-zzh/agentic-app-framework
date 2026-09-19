/**
 * Studio 段级错误边界：隔离 Studio 内页面与交互异常。
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"

export default function StudioError({
  error,
  reset
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="font-bold text-2xl tracking-tight">Studio 出错了</h1>
      <p className="max-w-lg text-muted-foreground text-sm">
        {error.message || "Studio 发生了未知错误"}
      </p>
      <Button type="button" onClick={reset}>
        重试
      </Button>
    </div>
  )
}
