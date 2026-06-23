/**
 * 首屏「今日待办」卡（v0.2.1 P3）
 *
 * 显示未完成的成长任务（最多 3 条），点击进入 /studio/me/tasks
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowRight, Trophy } from "lucide-react"
import Link from "next/link"
import { GlassCard, NeonChip } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { useGrowthTasks } from "@/lib/queries/use-growth-tasks"

export function HomeGrowthTasks() {
  const { data: tasks, isLoading } = useGrowthTasks()

  if (isLoading) {
    return <Skeleton className="h-32 w-full" />
  }

  // 仅显示未领取的任务，按 COMPLETED 优先 + sortOrder
  const todo = (tasks ?? [])
    .filter((t) => t.userStatus !== "CLAIMED")
    .sort((a, b) => {
      if (a.userStatus !== b.userStatus) return a.userStatus === "COMPLETED" ? -1 : 1
      return a.sortOrder - b.sortOrder
    })
    .slice(0, 3)

  if (todo.length === 0) return null

  return (
    <GlassCard glow="accent">
      <div className="space-y-3 p-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Trophy className="size-4 text-amber-400" />
            <h3 className="font-medium text-sm">今日待办</h3>
            <NeonChip tone="amber" size="sm">
              {todo.length}
            </NeonChip>
          </div>
          <Link
            href="/studio/me/tasks"
            className="flex items-center gap-1 text-muted-foreground text-xs transition-colors hover:text-foreground"
          >
            全部任务
            <ArrowRight className="size-3" />
          </Link>
        </div>
        <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
          {todo.map((t) => {
            const percent = Math.round((t.userProgress / t.targetCount) * 100)
            return (
              <Link
                key={t.id}
                href="/studio/me/tasks"
                className="flex items-center gap-2 rounded-lg border border-foreground/[0.08] bg-foreground/[0.02] px-3 py-2 transition-colors hover:bg-foreground/[0.06]"
              >
                <span className="text-xl">{t.icon ?? "🏆"}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-xs">{t.name}</p>
                  <div className="mt-0.5 flex items-center gap-1.5">
                    <div className="h-1 flex-1 overflow-hidden rounded-full bg-foreground/[0.06]">
                      <div
                        className={`h-full ${
                          t.userStatus === "COMPLETED" ? "bg-amber-400" : "bg-violet-400/60"
                        }`}
                        style={{ width: `${Math.min(100, percent)}%` }}
                      />
                    </div>
                    <span className="text-[10px] text-muted-foreground">+{t.rewardCredits}</span>
                  </div>
                </div>
              </Link>
            )
          })}
        </div>
      </div>
    </GlassCard>
  )
}
