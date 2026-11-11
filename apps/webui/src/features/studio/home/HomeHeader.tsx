/**
 * Studio 首页页头：左侧问候 + 昵称 + 日期时间 + 励志短句，中间画布标签页，右侧"自定义"与"全屏"入口。
 *
 * - overlay：大屏模式下作为浮层叠在画布上，自身不拦截画布操作，只有内部控件可点。
 *   窄屏时标签页放在画布区块顶部，页头不显示标签页。
 * - 时间每 30 秒刷新一次。依赖客户端当前时间与本地登录信息，调用方需在水合完成后再渲染。
 * - 全屏时隐藏"自定义"：其 Popover 渲染到 body，全屏元素之外不可见。
 * @author AaronZZH & Kiro
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Maximize2, Minimize2 } from "lucide-react"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/cn"
import { HomeCanvasTabs } from "./HomeCanvasTabs"
import { HomeLayoutCustomizer } from "./HomeLayoutCustomizer"
import { HomeMotto } from "./HomeMotto"

/** 时钟刷新间隔 */
const CLOCK_INTERVAL_MS = 30_000

interface HomeHeaderProps {
  /** 大屏浮层模式：显示画布标签页，且不拦截画布操作 */
  overlay: boolean
  /** 首页是否处于浏览器全屏 */
  fullscreen: boolean
  /** 浏览器是否允许全屏；不允许时不显示全屏按钮 */
  fullscreenSupported: boolean
  onToggleFullscreen: () => void
}

/** 按小时返回问候语 */
function greetingOf(hour: number): string {
  if (hour >= 5 && hour < 11) return "早上好"
  if (hour >= 11 && hour < 13) return "中午好"
  if (hour >= 13 && hour < 18) return "下午好"
  return "晚上好"
}

export function HomeHeader({
  overlay,
  fullscreen,
  fullscreenSupported,
  onToggleFullscreen
}: HomeHeaderProps) {
  const user = useAuthStore((state) => state.user)
  const [now, setNow] = useState(() => new Date())
  const name = user?.nickname || user?.username

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), CLOCK_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <header
      className={cn(
        "grid grid-cols-[1fr_auto_1fr] items-center gap-4",
        overlay && "pointer-events-none h-full *:pointer-events-auto"
      )}
    >
      <div className="flex min-w-0 items-baseline gap-3 justify-self-start">
        <h1 className={cn("truncate font-semibold", overlay ? "text-lg" : "text-2xl")}>
          {greetingOf(now.getHours())}
          {name ? `，${name}` : ""}
        </h1>
        <time
          dateTime={format(now, "yyyy-MM-dd'T'HH:mm")}
          className="hidden shrink-0 text-muted-foreground text-sm tabular-nums sm:inline"
        >
          {format(now, "MM-dd EEE HH:mm", { locale: zhCN })}
        </time>
        <HomeMotto now={now} className="hidden lg:block" />
      </div>
      {overlay ? <HomeCanvasTabs /> : <span aria-hidden="true" />}
      <div className="flex shrink-0 items-center gap-2 justify-self-end">
        {fullscreen ? null : <HomeLayoutCustomizer />}
        {fullscreenSupported ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="bg-background/50 backdrop-blur-md"
            onClick={onToggleFullscreen}
          >
            {fullscreen ? <Minimize2 /> : <Maximize2 />}
            {fullscreen ? "退出全屏" : "全屏"}
          </Button>
        ) : null}
      </div>
    </header>
  )
}
