/**
 * Studio 首页座舱面板外框：半透明底 + 细描边，标题前带强调竖条、下方渐隐分隔线。
 *
 * 可折叠面板（collapsible）：标题行右侧显示折叠箭头，整行可点击切换；折叠后只留标题行，
 * 标题旁显示 summary 摘要，展开与折叠有高度过渡。标题行内的 action 链接不会触发折叠。
 *
 * @example
 * <HomeWidgetCard
 *   title="积分"
 *   collapsible
 *   collapsed={collapsed}
 *   onToggle={toggle}
 *   summary="可用 1,280 · 近 30 天消耗 320"
 *   action={<Link href="/studio/me/credits">积分中心</Link>}
 * >
 *   ...
 * </HomeWidgetCard>
 * @author AaronZZH & Kiro
 */

import { ChevronUp } from "lucide-react"
import type * as React from "react"
import { useId } from "react"
import { cn } from "@/lib/utils/cn"

interface HomeWidgetCardProps {
  title: string
  /** 标题旁的标记（如"示例数据"） */
  badges?: React.ReactNode
  /** 标题行右侧操作 */
  action?: React.ReactNode
  /** 是否可折叠 */
  collapsible?: boolean
  /** 当前是否折叠，仅 collapsible 时生效 */
  collapsed?: boolean
  /** 切换折叠 */
  onToggle?: () => void
  /** 折叠后标题行显示的单行摘要 */
  summary?: React.ReactNode
  className?: string
  bodyClassName?: string
  children: React.ReactNode
}

export function HomeWidgetCard({
  title,
  badges,
  action,
  collapsible = false,
  collapsed = false,
  onToggle,
  summary,
  className,
  bodyClassName,
  children
}: HomeWidgetCardProps) {
  const titleId = useId()
  const bodyId = useId()
  const isCollapsed = collapsible && collapsed

  return (
    <section
      aria-labelledby={titleId}
      className={cn(
        "relative flex min-w-0 flex-col rounded-xl border border-foreground/10 bg-background/40 backdrop-blur-md",
        className
      )}
    >
      <div
        className={cn(
          "relative flex items-center gap-2 rounded-t-xl px-4 pt-3",
          isCollapsed && "rounded-b-xl pb-3",
          collapsible && "transition-colors hover:bg-foreground/[0.03]"
        )}
      >
        <span aria-hidden="true" className="h-4 w-1 shrink-0 rounded-full bg-primary" />
        <h2 id={titleId} className="shrink-0 font-semibold text-sm tracking-wide">
          {collapsible ? (
            // 伸展到整行的点击区域；action 链接通过 z-10 浮在其上
            <button
              type="button"
              aria-expanded={!isCollapsed}
              aria-controls={bodyId}
              onClick={onToggle}
              className="outline-none after:absolute after:inset-0 after:rounded-t-xl focus-visible:after:ring-2 focus-visible:after:ring-primary focus-visible:after:ring-inset"
            >
              {title}
            </button>
          ) : (
            title
          )}
        </h2>
        {badges}
        <div className="min-w-0 flex-1 truncate text-muted-foreground text-xs">
          {isCollapsed ? summary : null}
        </div>
        {action ? <div className="relative z-10 shrink-0">{action}</div> : null}
        {collapsible ? (
          <ChevronUp
            aria-hidden="true"
            className={cn(
              "pointer-events-none size-4 shrink-0 text-muted-foreground transition-transform duration-200",
              isCollapsed && "rotate-180"
            )}
          />
        ) : null}
      </div>
      <div
        id={bodyId}
        className={cn(
          "grid transition-[grid-template-rows] duration-300 ease-out",
          isCollapsed ? "grid-rows-[0fr]" : "grid-rows-[1fr]"
        )}
        inert={isCollapsed}
      >
        <div className="flex min-h-0 flex-col overflow-hidden">
          <div
            aria-hidden="true"
            className="mx-4 mt-2.5 mb-2.5 h-px bg-gradient-to-r from-primary/50 via-foreground/10 to-transparent"
          />
          <div className={cn("flex flex-1 flex-col px-4 pb-4", bodyClassName)}>{children}</div>
        </div>
      </div>
    </section>
  )
}
