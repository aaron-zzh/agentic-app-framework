/**
 * Studio 首页底部创作区：AIGC 创作输入 + 其下方的最近生成（60px），内容 60% 宽居中。
 *
 * 最近生成属于创作区，随创作区一起显示或隐藏，没有单独开关；调用方在创作区隐藏时不渲染本组件。
 * 大屏模式下浮在画布底部：背景透明、高度随内容（不再固定高度、不出滚动条），
 * 只有内容本身拦截鼠标，两侧空白处仍可直接拖动画布。内容右上角的把手收起整个创作区，
 * 收起状态由调用方持久化；内容实际高度通过 onHeightChange 回报，供画布留出底部可视区。
 * 窄屏下是普通区块，创作输入按内容高度展开，不提供收起（页面本身可滚动）。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronUp } from "lucide-react"
import { useEffect, useRef } from "react"
import { HOME_CREATE_WIDTH } from "./cockpit-dimensions"
import { HomeCreate } from "./HomeCreate"
import { HomeRecentAssets } from "./HomeRecentAssets"

interface HomeBottomDockProps {
  /** 大屏模式：浮在画布底部；否则为窄屏普通区块 */
  wide: boolean
  /** 创作区是否收起，仅大屏模式生效 */
  collapsed: boolean
  onToggleCollapsed: () => void
  /** 大屏模式下内容的实际高度（px，取整），收起时调用方自行按 0 处理 */
  onHeightChange?: (height: number) => void
}

export function HomeBottomDock({
  wide,
  collapsed,
  onToggleCollapsed,
  onHeightChange
}: HomeBottomDockProps) {
  const contentRef = useRef<HTMLDivElement>(null)

  // 回报内容高度：切换生成类型等导致输入区高度变化时同步给画布
  useEffect(() => {
    const element = contentRef.current
    if (!wide || !element || !onHeightChange) return
    const observer = new ResizeObserver(([entry]) => {
      if (entry) onHeightChange(Math.round(entry.contentRect.height))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [wide, onHeightChange])

  if (!wide) {
    return (
      <div className="flex flex-col gap-2 rounded-xl border border-foreground/10 bg-background/40 pt-2 backdrop-blur-sm">
        <div className="px-2">
          <HomeCreate />
        </div>
        <HomeRecentAssets />
      </div>
    )
  }

  const open = !collapsed

  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20">
      <div className="relative mx-auto" style={{ width: HOME_CREATE_WIDTH }}>
        <button
          type="button"
          aria-expanded={open}
          onClick={onToggleCollapsed}
          className="pointer-events-auto absolute -top-6 right-0 z-10 flex h-6 items-center gap-1 rounded-t-md border border-foreground/10 border-b-0 bg-background/60 px-2 text-muted-foreground text-xs outline-none backdrop-blur-sm transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary"
        >
          {collapsed ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
          {collapsed ? "展开创作" : "收起创作"}
        </button>

        {/* grid 行高 0fr ↔ 1fr 实现按内容高度的收起动画 */}
        <div
          className="grid transition-[grid-template-rows] duration-300 ease-out"
          style={{ gridTemplateRows: open ? "1fr" : "0fr" }}
          inert={!open}
        >
          <div className="min-h-0 overflow-hidden">
            {/* 最近生成在输入框下方；缩略图预览向上浮出到输入区，仍在内容范围内 */}
            <div ref={contentRef} className="pointer-events-auto flex flex-col pt-2">
              <HomeCreate />
              <HomeRecentAssets className="px-0" />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
