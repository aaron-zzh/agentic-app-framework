/**
 * Studio 首页座舱布局。
 *
 * - 大屏模式（视口 ≥ 1280px 或全屏）：中央画布铺满作背景，页头、左栏、右栏、底部区作为浮层叠在画布上；
 *   容器不滚动，浮层之外的区域可直接拖动、缩放画布。
 * - 窄屏：纵向堆叠，页面可滚动，画布为固定高度区块。
 * 面板显示由 HOME_WIDGETS 与用户隐藏偏好决定；某侧面板全部隐藏时该侧浮层不渲染。
 * @author AaronZZH & Kiro
 */

"use client"

import { type ReactNode, useMemo, useState } from "react"
import { useStudioHomeLayoutStore } from "@/lib/store/studio-home-layout-store"
import { HomeCanvas } from "./canvas/HomeCanvas"
import type { CanvasFrame } from "./canvas/types"
import {
  canvasFitPadding,
  canvasVisibleArea,
  HOME_HEADER_HEIGHT,
  HOME_LEFT_PANEL_WIDTH,
  HOME_PANEL_GAP,
  HOME_RIGHT_PANEL_WIDTH,
  NO_CANVAS_INSETS
} from "./cockpit-dimensions"
import { HomeAgentRunStats } from "./HomeAgentRunStats"
import { HomeBottomDock } from "./HomeBottomDock"
import { HomeCanvasTabs } from "./HomeCanvasTabs"
import { HomeCreditsPanel } from "./HomeCreditsPanel"
import { HomeHeader } from "./HomeHeader"
import { HomeResourceList } from "./HomeResourceList"
import { type HomeWidgetKey, resolveHomeWidgetVisibility, widgetsInRegion } from "./home-widgets"

/** 窄屏下画布不接管滚轮，避免抢走页面滚动 */
const NARROW_FRAME: CanvasFrame = {
  fitPadding: canvasFitPadding(NO_CANVAS_INSETS),
  area: canvasVisibleArea(NO_CANVAS_INSETS),
  wheelZoom: false
}

/** 底部创作区首次测量前的估计高度（px）：创作输入约 190 + 最近生成 60 */
const DOCK_HEIGHT_ESTIMATE = 250

interface HomeCockpitLayoutProps {
  /** 是否大屏模式 */
  wide: boolean
  fullscreen: boolean
  fullscreenSupported: boolean
  onToggleFullscreen: () => void
}

/** 左右栏面板（底部区由 HomeBottomDock 负责） */
function renderSideWidget(key: HomeWidgetKey, wide: boolean): ReactNode {
  switch (key) {
    case "resources":
      return <HomeResourceList key={key} layout={wide ? "list" : "grid"} />
    case "credits":
      return <HomeCreditsPanel key={key} />
    case "agent-runs":
      return <HomeAgentRunStats key={key} />
    default:
      return null
  }
}

export function HomeCockpitLayout({
  wide,
  fullscreen,
  fullscreenSupported,
  onToggleFullscreen
}: HomeCockpitLayoutProps) {
  const hiddenKeys = useStudioHomeLayoutStore((state) => state.hiddenKeys)
  const bottomCollapsed = useStudioHomeLayoutStore((state) => state.bottomCollapsed)
  const setBottomCollapsed = useStudioHomeLayoutStore((state) => state.setBottomCollapsed)
  const visibility = resolveHomeWidgetVisibility(hiddenKeys)
  const leftWidgets = widgetsInRegion("left", visibility)
  const rightWidgets = widgetsInRegion("right", visibility)
  const createVisible = visibility.create
  // 创作区浮在画布底部、高度随内容，按实测高度给画布留出底部可视区；首次测量前用估计值
  const [dockHeight, setDockHeight] = useState(DOCK_HEIGHT_ESTIMATE)

  const hasLeft = leftWidgets.length > 0
  const hasRight = rightWidgets.length > 0
  const bottomInset = createVisible && !bottomCollapsed ? dockHeight : 0

  // 画布适应范围延伸到底部：底部创作区背景透明、浮在画布上，不再为它留出可视区
  // 只依赖原始值，避免每次渲染生成新对象导致画布反复适应视图
  const wideFrame = useMemo<CanvasFrame>(() => {
    const insets = { top: HOME_HEADER_HEIGHT, bottom: 0, left: hasLeft, right: hasRight }
    return {
      fitPadding: canvasFitPadding(insets),
      area: canvasVisibleArea(insets),
      wheelZoom: true
    }
  }, [hasLeft, hasRight])

  const dock = (
    <HomeBottomDock
      wide={wide}
      collapsed={bottomCollapsed}
      onToggleCollapsed={() => setBottomCollapsed(!bottomCollapsed)}
      onHeightChange={setDockHeight}
    />
  )
  const header = (
    <HomeHeader
      overlay={wide}
      fullscreen={fullscreen}
      fullscreenSupported={fullscreenSupported}
      onToggleFullscreen={onToggleFullscreen}
    />
  )

  if (!wide) {
    return (
      <div className="relative flex flex-col gap-6 px-6 py-6">
        {header}
        <section aria-label="协作画布" className="flex flex-col gap-3">
          <HomeCanvasTabs className="self-center" />
          <HomeCanvas
            frame={NARROW_FRAME}
            className="h-[360px] rounded-xl border border-foreground/10 md:h-[480px]"
          />
        </section>
        {hasLeft ? leftWidgets.map((widget) => renderSideWidget(widget.key, false)) : null}
        {hasRight ? (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {rightWidgets.map((widget) => renderSideWidget(widget.key, false))}
          </div>
        ) : null}
        {createVisible ? dock : null}
      </div>
    )
  }

  const sideTop = HOME_HEADER_HEIGHT + HOME_PANEL_GAP / 2
  // 底部区上沿有 24px 高的折叠把手，侧栏需让开
  const sideBottom = bottomInset + (createVisible ? HOME_PANEL_GAP * 2 : HOME_PANEL_GAP)

  return (
    <div className="relative h-full min-h-[640px] overflow-hidden">
      <HomeCanvas frame={wideFrame} className="absolute inset-0" />

      <div
        className="pointer-events-none absolute inset-x-0 top-0 z-20 px-4"
        style={{ height: HOME_HEADER_HEIGHT }}
      >
        {header}
      </div>

      {hasLeft ? (
        <aside
          aria-label="我的资源"
          className="absolute z-20 flex flex-col gap-3 overflow-y-auto"
          style={{
            top: sideTop,
            bottom: sideBottom,
            left: HOME_PANEL_GAP,
            width: HOME_LEFT_PANEL_WIDTH
          }}
        >
          {leftWidgets.map((widget) => renderSideWidget(widget.key, true))}
        </aside>
      ) : null}

      {hasRight ? (
        <aside
          aria-label="统计"
          className="absolute z-20 flex flex-col gap-3 overflow-y-auto"
          style={{
            top: sideTop,
            bottom: sideBottom,
            right: HOME_PANEL_GAP,
            width: HOME_RIGHT_PANEL_WIDTH
          }}
        >
          {rightWidgets.map((widget) => renderSideWidget(widget.key, true))}
        </aside>
      ) : null}

      {createVisible ? dock : null}
    </div>
  )
}
