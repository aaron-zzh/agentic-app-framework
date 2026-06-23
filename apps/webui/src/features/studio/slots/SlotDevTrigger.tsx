/**
 * SlotDevTrigger——开发期演示触发器
 *
 * 用于演示动态面板槽：
 * - "查询天气" → 模拟后端 WS 推送 weather slot
 * - "看任务" → recent-tasks slot
 * - "看通知" → notifications slot
 * - "看积分" → credits slot
 * - "项目#1" → project-summary slot
 *
 * 生产环境：替换为后端 SSE/WS 监听器，详见 useStudioSlotStream（待实现）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Wand2 } from "lucide-react"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { useSlotStore } from "./store"

const DEMOS: Array<{
  label: string
  panelType: import("./store").SlotPanelType
  payload?: Record<string, unknown>
}> = [
  { label: "🌤 查询天气（北京）", panelType: "weather", payload: { city: "北京" } },
  { label: "🌧 查询天气（上海）", panelType: "weather", payload: { city: "上海" } },
  { label: "📋 看任务进度", panelType: "recent-tasks" },
  { label: "🔔 看通知", panelType: "notifications" },
  { label: "💰 看积分", panelType: "credits" },
  { label: "📁 项目摘要 #1", panelType: "project-summary", payload: { projectId: 1 } }
]

export function SlotDevTrigger() {
  const open = useSlotStore((s) => s.openSlot)

  return (
    <Popover>
      <PopoverTrigger
        render={
          <button
            type="button"
            className="fixed bottom-20 left-20 z-40 flex size-10 items-center justify-center rounded-full bg-amber-500/90 text-white shadow-amber-500/30 shadow-lg transition-transform hover:scale-105"
            aria-label="演示触发器（开发期）"
          />
        }
      >
        <Wand2 className="size-4" />
      </PopoverTrigger>
      <PopoverContent side="left" align="end" className="w-56 p-0">
        <div className="border-foreground/6 border-b px-3 py-2">
          <p className="font-medium text-xs">演示触发器</p>
          <p className="mt-0.5 text-[10px] text-muted-foreground">
            模拟后端 WS 推送动态面板（仅开发期）
          </p>
        </div>
        <div className="max-h-72 overflow-y-auto py-1">
          {DEMOS.map((d) => (
            <button
              key={d.label}
              type="button"
              onClick={() => open({ panelType: d.panelType, payload: d.payload })}
              className="block w-full px-3 py-1.5 text-left text-xs transition-colors hover:bg-foreground/4"
            >
              {d.label}
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
