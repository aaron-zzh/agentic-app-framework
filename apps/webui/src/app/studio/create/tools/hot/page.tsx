/**
 * /studio/create/tools/hot——热点跟踪（实时接口版）
 * GET /api/aigc/trending → 20 条热点列表
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { ArrowUpRight, RefreshCw, TrendingUp } from "lucide-react"
import Link from "next/link"
import { LottieIcon } from "@/components/animate"
import { GlassCard, GlassCardBody, NeonChip, SectionHaze } from "@/components/studio"
import { backendRequest } from "@/lib/api/rest/backend-client"

interface TrendingItem {
  rank: number
  title: string
  summary: string
  tag: string
  suggestion: string
}

const TAG_TONE: Record<string, "violet" | "cyan" | "amber" | "rose" | "emerald"> = {
  爆款: "rose",
  上升: "emerald",
  新闻: "cyan",
  娱乐: "violet",
  科技: "amber",
  社会: "cyan"
}

export default function StudioToolsHotPage() {
  const { data, isLoading, isFetching, refetch } = useQuery<TrendingItem[]>({
    queryKey: ["trending"],
    queryFn: () => backendRequest("/aigc/trending", { timeout: 120_000 }),
    staleTime: 5 * 60 * 1000 // 5分钟缓存
  })

  const items = data ?? []

  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="violet" />
      <div className="relative space-y-6">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <TrendingUp className="size-5 text-cyan-400" />
            <h1 className="font-semibold text-xl">热点跟踪</h1>
          </div>
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-1.5 text-muted-foreground text-xs transition-colors hover:text-foreground"
          >
            <RefreshCw className={`size-3.5 ${isFetching ? "animate-spin" : ""}`} />
            刷新
          </button>
        </div>

        <p className="text-muted-foreground text-sm">AI 实时搜索当前热点，点击借势创作文案</p>

        {isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={`sk-${i}`} className="h-16 animate-pulse rounded-xl bg-foreground/[0.04]" />
            ))}
          </div>
        )}

        {!isLoading && items.length === 0 && (
          <div className="flex flex-col items-center justify-center gap-3 py-16 text-muted-foreground">
            <LottieIcon name="cat" width={200} height={200} loop />
            <p className="text-sm">暂无热点数据，请点击刷新重试</p>
          </div>
        )}

        <div className="space-y-3">
          {items.map((item, idx) => (
            <GlassCard key={item.rank} glow="none">
              <GlassCardBody className="flex items-start gap-4">
                {/* 排名 */}
                <span
                  className={`w-7 shrink-0 pt-0.5 text-center font-bold text-lg tabular-nums ${idx < 3 ? "text-amber-400" : "text-muted-foreground/40"}`}
                >
                  {item.rank}
                </span>

                {/* 内容 */}
                <div className="min-w-0 flex-1 space-y-1">
                  <p className="font-medium text-sm">{item.title}</p>
                  <p className="line-clamp-2 text-muted-foreground text-xs leading-5">
                    {item.summary}
                  </p>
                  <div className="flex items-center gap-2 pt-0.5">
                    <NeonChip tone={TAG_TONE[item.tag] ?? "violet"} size="sm">
                      {item.tag}
                    </NeonChip>
                    <span className="text-muted-foreground/70 text-xs">{item.suggestion}</span>
                  </div>
                </div>

                {/* 借势 CTA */}
                <Link
                  href={`/studio/create/copy?topic=${encodeURIComponent(item.title)}&notes=${encodeURIComponent(item.suggestion)}`}
                  className="flex shrink-0 items-center gap-1 rounded-md px-2 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
                >
                  <span>借势创作</span>
                  <ArrowUpRight className="size-3.5" />
                </Link>
              </GlassCardBody>
            </GlassCard>
          ))}
        </div>
      </div>
    </div>
  )
}
