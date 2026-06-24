/**
 * Studio 首屏-数据胶囊行
 *
 * 4 张数据胶囊：今日生成 / 作品总数 / 积分余额 / 待办任务
 * 数据复用现有 hooks，避免新增 endpoint。
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { ArrowRight, CheckSquare, FolderKanban, Sparkles, Wallet } from "lucide-react"
import Link from "next/link"
import { DataCapsule } from "@/components/studio"
import { request } from "@/lib/api/rest/entity/crud"
import { useCreditBalance } from "@/lib/queries/use-credits"

function useTodayTaskCount() {
  return useQuery({
    queryKey: ["aigc", "tasks", "today-count"] as const,
    queryFn: () => request<number>("/aigc/tasks/today-count")
  })
}

function useAiAssetCount() {
  return useQuery({
    queryKey: ["aigc", "assets", "ai-count"] as const,
    queryFn: () => request<number>("/aigc/assets/ai-count")
  })
}

function useDocCount() {
  return useQuery({
    queryKey: ["docs", "count"] as const,
    queryFn: () => request<number>("/docs/count")
  })
}

export function HomeDataCapsules() {
  const { data: balance, isLoading: balanceLoading } = useCreditBalance()
  const { data: todayCount, isLoading: todayLoading } = useTodayTaskCount()
  const { data: aiAssetCount, isLoading: aiAssetLoading } = useAiAssetCount()
  const { data: docCount, isLoading: docLoading } = useDocCount()

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Link href="/studio/assets/history" className="group">
        <DataCapsule
          label="今日生成"
          value={todayCount ?? 0}
          unit="次"
          loading={todayLoading}
          icon={<Sparkles className="size-4" />}
          tone="violet"
          action={
            <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          }
        />
      </Link>
      <Link href="/studio/assets/works" className="group">
        <DataCapsule
          label="AI 素材"
          value={aiAssetCount ?? 0}
          unit="个"
          loading={aiAssetLoading}
          icon={<FolderKanban className="size-4" />}
          tone="cyan"
          action={
            <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          }
        />
      </Link>
      <Link href="/studio/me/credits" className="group">
        <DataCapsule
          label="积分余额"
          value={(balance?.balance ?? 0).toLocaleString()}
          loading={balanceLoading}
          icon={<Wallet className="size-4" />}
          tone="amber"
          action={
            <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          }
        />
      </Link>
      <Link href="/studio/knowledge/docs" className="group">
        <DataCapsule
          label="我的文档"
          value={docCount ?? 0}
          unit="篇"
          loading={docLoading}
          icon={<CheckSquare className="size-4" />}
          tone="emerald"
          action={
            <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          }
        />
      </Link>
    </div>
  )
}
