/**
 * 成长任务页（v0.2.1 P3）
 *
 * 列出新手 / 每日 / 成就 三类任务，显示进度，达成后可领取奖励
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Coins, Loader2, Sparkles, Trophy } from "lucide-react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type GrowthTaskVO,
  useClaimGrowthTask,
  useGrowthTasks
} from "@/lib/queries/use-growth-tasks"
import { cn } from "@/lib/utils"

const CATEGORY_LABELS: Record<GrowthTaskVO["category"], string> = {
  ONBOARDING: "新手任务",
  DAILY: "每日任务",
  ACHIEVEMENT: "成就任务"
}

function TaskCard({ task }: { task: GrowthTaskVO }) {
  const claim = useClaimGrowthTask()
  const isComplete = task.userStatus === "COMPLETED"
  const isClaimed = task.userStatus === "CLAIMED"
  const progressPercent = Math.min(100, Math.round((task.userProgress / task.targetCount) * 100))

  const handleClaim = async () => {
    try {
      await claim.mutateAsync(task.id)
      toast.success(`已领取：+${task.rewardCredits} 积分`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "领取失败")
    }
  }

  return (
    <GlassCard glow={isComplete ? "violet" : "none"}>
      <div className="space-y-3 p-5">
        <div className="flex items-start gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-foreground/[0.04] text-xl">
            {task.icon ?? "🏆"}
          </div>
          <div className="flex-1 space-y-1">
            <div className="flex items-center gap-2">
              <h3 className="font-semibold text-sm">{task.name}</h3>
              {isClaimed && (
                <NeonChip tone="emerald" size="sm">
                  <CheckCircle2 className="size-3" />
                  已领取
                </NeonChip>
              )}
            </div>
            {task.description && (
              <p className="text-muted-foreground text-xs leading-relaxed">{task.description}</p>
            )}
          </div>
        </div>

        {/* 进度条 */}
        <div className="space-y-1">
          <div className="flex items-center justify-between text-xs">
            <span className="text-muted-foreground">
              进度 {task.userProgress}/{task.targetCount}
            </span>
            <span
              className={cn(
                "flex items-center gap-1",
                isClaimed ? "text-emerald-300" : "text-amber-300"
              )}
            >
              <Coins className="size-3" />+{task.rewardCredits} 积分
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-foreground/[0.06]">
            <div
              className={cn(
                "h-full transition-all duration-500",
                isClaimed
                  ? "bg-emerald-400/60"
                  : isComplete
                    ? "bg-amber-400/80"
                    : "bg-violet-400/60"
              )}
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>

        {/* 操作 */}
        {isComplete && (
          <GlowButton
            tone="primary"
            size="sm"
            className="w-full"
            onClick={handleClaim}
            disabled={claim.isPending}
          >
            {claim.isPending ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Sparkles className="size-3.5" />
            )}
            领取奖励
          </GlowButton>
        )}
      </div>
    </GlassCard>
  )
}

export default function StudioMeTasksPage() {
  const { data: tasks, isLoading } = useGrowthTasks()

  // 按分类分组
  const grouped: Record<string, GrowthTaskVO[]> = {}
  for (const t of tasks ?? []) {
    grouped[t.category] = grouped[t.category] ?? []
    grouped[t.category]?.push(t)
  }

  return (
    <div className="relative">
      <SectionHaze variant="amber" />
      <div className="mx-auto max-w-4xl space-y-6 p-6">
        <header className="space-y-2">
          <div className="flex items-center gap-2">
            <Trophy className="size-5 text-amber-400" />
            <h1 className="font-semibold text-xl">成长任务</h1>
          </div>
          <p className="text-muted-foreground text-sm">完成任务领取积分奖励，解锁更多创作能力</p>
        </header>

        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={`gt-${i}`} className="h-44 w-full" />
            ))}
          </div>
        ) : !tasks || tasks.length === 0 ? (
          <p className="text-center text-muted-foreground text-sm">暂无任务</p>
        ) : (
          (["ONBOARDING", "DAILY", "ACHIEVEMENT"] as const).map((cat) => {
            const list = grouped[cat] ?? []
            if (list.length === 0) return null
            return (
              <section key={cat} className="space-y-3">
                <h2 className="font-medium text-base">{CATEGORY_LABELS[cat]}</h2>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  {list.map((t) => (
                    <TaskCard key={t.id} task={t} />
                  ))}
                </div>
              </section>
            )
          })
        )}
      </div>
    </div>
  )
}
