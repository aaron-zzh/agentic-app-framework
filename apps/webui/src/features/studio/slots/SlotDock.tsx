/**
 * SlotDock——动态面板槽容器
 *
 * 布局：固定在主区右侧，横向排列 5 个面板（resizable + 可关闭）
 * 折叠：点折叠按钮收起为竖条
 * 最近列表：通过悬浮按钮展开
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronRight, Clock, History, X } from "lucide-react"
import { useState } from "react"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"
import { SLOT_REGISTRY } from "./registry"
import { SlotPanel } from "./SlotPanel"
import { useSlotStore } from "./store"

export function SlotDock() {
  const active = useSlotStore((s) => s.active)
  const recent = useSlotStore((s) => s.recent)
  const collapsed = useSlotStore((s) => s.collapsed)
  const toggleCollapsed = useSlotStore((s) => s.toggleCollapsed)
  const reopenRecent = useSlotStore((s) => s.reopenRecent)
  const clearRecent = useSlotStore((s) => s.clearRecent)

  const [historyOpen, setHistoryOpen] = useState(false)

  // 完全无活动 + 无最近 → 不渲染
  if (active.length === 0 && recent.length === 0) return null

  // 折叠态
  if (collapsed) {
    return (
      <div className="pointer-events-none absolute inset-y-0 right-0 z-30 flex items-center">
        <button
          type="button"
          onClick={toggleCollapsed}
          className={cn(
            "pointer-events-auto rounded-l-md border border-foreground/[0.08] border-r-0 bg-background/80 px-1 py-3 backdrop-blur",
            "transition-colors hover:bg-foreground/[0.06]"
          )}
          aria-label="展开面板槽"
        >
          <ChevronRight className="size-3.5 rotate-180" />
          {active.length > 0 && (
            <span className="mt-1 block rounded-full bg-primary px-1 text-[10px] text-primary-foreground">
              {active.length}
            </span>
          )}
        </button>
      </div>
    )
  }

  return (
    <div
      className={cn(
        "pointer-events-none absolute inset-y-0 right-0 z-30 flex items-stretch p-3",
        "max-w-[calc(100%-200px)]"
      )}
    >
      <div className="pointer-events-auto flex items-stretch gap-2 self-end">
        {/* 工具栏 */}
        <div className="flex flex-col items-center gap-1 self-start pt-2">
          <Popover open={historyOpen} onOpenChange={setHistoryOpen}>
            <PopoverTrigger
              render={
                <button
                  type="button"
                  className="rounded-md border border-foreground/[0.08] bg-background/80 p-1.5 backdrop-blur transition-colors hover:bg-foreground/[0.06]"
                  aria-label="最近面板"
                />
              }
            >
              <History className="size-3.5" />
            </PopoverTrigger>
            <PopoverContent side="left" align="end" className="w-72 p-0">
              <div className="flex items-center justify-between border-foreground/[0.06] border-b px-3 py-2">
                <div className="flex items-center gap-1.5">
                  <Clock className="size-3.5 opacity-70" />
                  <span className="font-medium text-xs">最近面板</span>
                </div>
                {recent.length > 0 && (
                  <button
                    type="button"
                    onClick={clearRecent}
                    className="text-muted-foreground text-xs transition-colors hover:text-foreground"
                  >
                    清空
                  </button>
                )}
              </div>
              <div className="max-h-72 overflow-y-auto py-1">
                {recent.length === 0 ? (
                  <p className="px-3 py-4 text-center text-muted-foreground text-xs">暂无记录</p>
                ) : (
                  recent.map((r) => {
                    const def = SLOT_REGISTRY[r.panelType]
                    const Icon = def.icon
                    return (
                      <button
                        key={r.id}
                        type="button"
                        onClick={() => {
                          reopenRecent(r.id)
                          setHistoryOpen(false)
                        }}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-foreground/[0.04]"
                      >
                        <Icon className="size-3.5 opacity-70" />
                        <span className="flex-1 truncate text-xs">{def.title}</span>
                        {r.payload && Object.keys(r.payload).length > 0 && (
                          <span className="text-muted-foreground text-xs">
                            {Object.values(r.payload)[0]?.toString().slice(0, 12)}
                          </span>
                        )}
                      </button>
                    )
                  })
                )}
              </div>
            </PopoverContent>
          </Popover>

          <button
            type="button"
            onClick={toggleCollapsed}
            className="rounded-md border border-foreground/[0.08] bg-background/80 p-1.5 backdrop-blur transition-colors hover:bg-foreground/[0.06]"
            aria-label="折叠面板槽"
          >
            <X className="size-3.5" />
          </button>
        </div>

        {/* 面板列表 */}
        {active.length > 0 && (
          <div className="flex items-stretch gap-2">
            {active.map((slot) => (
              <SlotPanel key={slot.id} slot={slot} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
