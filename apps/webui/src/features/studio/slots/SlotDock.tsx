/**
 * SlotDock——动态面板槽容器
 *
 * 右侧常驻 ChevronRight 按钮，点击弹出面板列表；无数据时隐藏
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronRight } from "lucide-react"
import { useState } from "react"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"
import { SlotPanel } from "./SlotPanel"
import { useSlotStore } from "./store"

export function SlotDock() {
  const active = useSlotStore((s) => s.active)
  const [open, setOpen] = useState(false)

  if (active.length === 0) return null

  return (
    <>
      {/* 常驻触发按钮 */}
      <div className="pointer-events-none absolute inset-y-0 right-0 z-30 flex items-center">
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger
            render={
              <button
                type="button"
                className={cn(
                  "pointer-events-auto rounded-l-md border border-foreground/[0.08] border-r-0",
                  "bg-background/80 px-1 py-3 backdrop-blur transition-colors hover:bg-foreground/[0.06]"
                )}
                aria-label="展开面板"
              />
            }
          >
            <ChevronRight className="size-3.5 rotate-180" />
          </PopoverTrigger>
          <PopoverContent side="left" align="center" className="w-auto max-w-[90vw] p-3">
            <div className="flex items-stretch gap-2">
              {active.map((slot) => (
                <SlotPanel key={slot.id} slot={slot} />
              ))}
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </>
  )
}
