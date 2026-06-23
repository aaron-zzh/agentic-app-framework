/**
 * Studio 首屏-数据胶囊行
 *
 * 4 张数据胶囊：今日生成 / 作品总数 / 积分余额 / 待办任务
 * 数据复用现有 hooks，避免新增 endpoint。
 */

"use client"

import { CheckSquare, FolderKanban, Sparkles, Wallet } from "lucide-react"
import { useMemo } from "react"
import { DataCapsule } from "@/components/studio"
import { useAigcProjects } from "@/lib/queries/use-aigc-projects"
import { useCreditBalance } from "@/lib/queries/use-credits"
import { useGenerationHistory } from "@/lib/queries/use-generation-history"
import { useTodos } from "@/lib/queries/use-todos"

export function HomeDataCapsules() {
  const { data: projects, isLoading: projectsLoading } = useAigcProjects({ pageSize: 1 })
  const { data: balance, isLoading: balanceLoading } = useCreditBalance()

  // 今日生成：拉最近 100 条，过滤 createTime 为今日
  const { data: historyPage, isLoading: histLoading } = useGenerationHistory(0, 100)
  const todayCount = useMemo(() => {
    if (!historyPage) return 0
    const todayStr = new Date().toDateString()
    return (historyPage.list ?? []).filter((item) => {
      try {
        const t =
          (item as { createTime?: string; createdAt?: string }).createTime ??
          (item as { createdAt?: string }).createdAt ??
          ""
        return new Date(t).toDateString() === todayStr
      } catch {
        return false
      }
    }).length
  }, [historyPage])

  // 待办任务：未完成数
  const { data: todosPage, isLoading: todosLoading } = useTodos({ status: "pending", pageSize: 1 })
  const pendingTodos = todosPage?.total ?? 0

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <DataCapsule
        label="今日生成"
        value={todayCount}
        unit="次"
        loading={histLoading}
        icon={<Sparkles className="size-4" />}
        tone="violet"
      />
      <DataCapsule
        label="作品总数"
        value={projects?.total ?? 0}
        unit="个"
        loading={projectsLoading}
        icon={<FolderKanban className="size-4" />}
        tone="cyan"
      />
      <DataCapsule
        label="积分余额"
        value={(balance?.balance ?? 0).toLocaleString()}
        loading={balanceLoading}
        icon={<Wallet className="size-4" />}
        tone="amber"
      />
      <DataCapsule
        label="待办任务"
        value={pendingTodos}
        unit="项"
        loading={todosLoading}
        icon={<CheckSquare className="size-4" />}
        tone="emerald"
      />
    </div>
  )
}
