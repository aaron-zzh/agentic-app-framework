/**
 * Content Studio 首页——座舱大屏：中央多智能体协作画布，四周资源、积分、运行统计、创作与最近生成浮层。
 *
 * 本文件只负责页面容器与屏幕模式；布局见 HomeCockpitLayout，面板定义见 HOME_WIDGETS。
 * 大屏模式（视口 ≥ 1280px 或全屏）下页面铺满主内容区、不滚动；窄屏下纵向堆叠可滚动。
 * @author AaronZZH & Kiro
 */

"use client"

import { useRef } from "react"
import { HomeCockpitLayout, HomeSkeleton, useHomeScreenMode } from "@/features/studio/home"
import { useHydrated } from "@/lib/hooks/use-hydrated"
import { cn } from "@/lib/utils/cn"

export default function StudioHomePage() {
  // 水合完成后才读取本地偏好、当前时间与登录信息，避免与服务端渲染不一致
  const hydrated = useHydrated()
  const containerRef = useRef<HTMLDivElement>(null)
  const { fit, fullscreen, fullscreenSupported, toggleFullscreen } = useHomeScreenMode(containerRef)

  return (
    <div
      ref={containerRef}
      className={cn(
        "relative bg-background",
        fit ? "h-full" : "min-h-full",
        // 全屏元素由浏览器设为满屏尺寸，窄屏堆叠时需自带滚动
        fullscreen && "overflow-y-auto"
      )}
    >
      {hydrated ? (
        <HomeCockpitLayout
          wide={fit}
          fullscreen={fullscreen}
          fullscreenSupported={fullscreenSupported}
          onToggleFullscreen={toggleFullscreen}
        />
      ) : (
        <div className="mx-auto max-w-7xl px-6 py-8">
          <HomeSkeleton />
        </div>
      )}
    </div>
  )
}
