"use client"

/**
 * LoginSuccessOverlay——登录成功过渡卡片
 *
 * 与登录卡片在同一容器内联渲染（同 max-w-sm 宽度），不另加 bg/shadow/rounded，
 * 直接坐在 auth layout 的模糊背景上，视觉上与登录卡片完全等宽等高；
 * confetti 铺满区域，success 图标等比例放大。约 2.4s 后回调跳转。
 */

import { useEffect } from "react"
import { Button } from "@/components/ui/button"
import { LottieIcon } from "@/components/animate/LottieIcon"

interface LoginSuccessOverlayProps {
  username?: string
  onDone: () => void
}

export function LoginSuccessOverlay({ username, onDone }: LoginSuccessOverlayProps) {
  useEffect(() => {
    const timer = setTimeout(onDone, 2400)
    return () => clearTimeout(timer)
  }, [onDone])

  return (
    <div className="relative flex min-h-[560px] w-full flex-col items-center justify-center gap-6">
      {/* confetti 铺满区域，随容器自适应 */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <LottieIcon name="confetti" width="100%" height="100%" loop />
      </div>

      {/* 内容 */}
      <div className="relative z-10 flex flex-col items-center gap-6">
        <LottieIcon name="success" width={180} height={180} loop={false} />
        <div className="text-center">
          <p className="font-bold text-2xl">登录成功！</p>
          {username && <p className="mt-2 text-muted-foreground text-sm">欢迎回来，{username}</p>}
        </div>
        <p className="text-muted-foreground text-xs">正在跳转…</p>
        <Button
          variant="ghost"
          size="sm"
          onClick={onDone}
          className="text-primary text-xs underline underline-offset-2"
        >
          立即跳转
        </Button>
      </div>
    </div>
  )
}
