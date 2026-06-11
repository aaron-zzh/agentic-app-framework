/**
 * 生成历史面板——按时间倒序展示历史记录，支持类型筛选和参数复用
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronUp, Clock, Image, Video } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { GenerationHistoryItem } from "@/lib/api/rest/ai/generation-history"
import { useGenerationHistory } from "@/lib/queries/use-generation-history"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "../store"

interface GenerationHistoryProps {
  className?: string
}

/** 单条历史记录卡片 */
function HistoryItem({ item }: { item: GenerationHistoryItem }) {
  const setPrompt = useAigcStore((s) => s.setPrompt)
  const setModel = useAigcStore((s) => s.setModel)

  /** 点击复用参数 */
  function handleReuse() {
    setPrompt(item.prompt)
    setModel(item.model)
  }

  return (
    <button
      type="button"
      onClick={handleReuse}
      className="flex w-full gap-2 rounded-lg border border-border/50 p-2 text-left transition-colors hover:border-primary/50 hover:bg-muted/50"
    >
      {/* 缩略图 */}
      <div className="size-12 shrink-0 overflow-hidden rounded-md bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态历史缩略图 */}
        <img src={item.thumbnail} alt={item.prompt} className="size-full object-cover" />
      </div>
      {/* 信息 */}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-foreground text-xs">{item.prompt}</span>
        <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
          {item.type === "image" ? <Image className="size-3" /> : <Video className="size-3" />}
          <span>{item.model}</span>
          <span>·</span>
          <span>{new Date(item.createdAt).toLocaleDateString()}</span>
        </div>
      </div>
    </button>
  )
}

export function GenerationHistory({ className }: GenerationHistoryProps) {
  const [collapsed, setCollapsed] = useState(true)
  const [typeFilter, setTypeFilter] = useState<"all" | "image" | "video">("all")

  const userId = useAuthStore((s) => s.user?.id) ?? ""

  const { data, isLoading } = useGenerationHistory(
    userId,
    0,
    20,
    typeFilter === "all" ? undefined : typeFilter
  )

  return (
    <div className={cn("border-border/50 border-t", className)}>
      {/* 折叠头部 */}
      <Button
        variant="ghost"
        size="sm"
        className="flex w-full items-center justify-between px-4 py-2"
        onClick={() => setCollapsed(!collapsed)}
      >
        <span className="flex items-center gap-1.5 font-medium text-xs">
          <Clock className="size-3.5" />
          生成历史
        </span>
        {collapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
      </Button>

      {/* 展开内容 */}
      {!collapsed && (
        <div className="flex flex-col gap-2 px-3 pb-3">
          {/* 类型筛选 */}
          <Tabs
            value={typeFilter}
            onValueChange={(v) => setTypeFilter(v as "all" | "image" | "video")}
          >
            <TabsList className="h-7 w-full">
              <TabsTrigger value="all" className="h-5 text-[10px]">
                全部
              </TabsTrigger>
              <TabsTrigger value="image" className="h-5 text-[10px]">
                图片
              </TabsTrigger>
              <TabsTrigger value="video" className="h-5 text-[10px]">
                视频
              </TabsTrigger>
            </TabsList>
          </Tabs>

          {/* 列表 */}
          <div className="flex max-h-60 flex-col gap-1.5 overflow-y-auto">
            {isLoading && (
              <>
                <Skeleton className="h-16 w-full rounded-lg" />
                <Skeleton className="h-16 w-full rounded-lg" />
                <Skeleton className="h-16 w-full rounded-lg" />
              </>
            )}
            {data?.list.map((item) => (
              <HistoryItem key={item.id} item={item} />
            ))}
            {!isLoading && (!data?.list || data.list.length === 0) && (
              <p className="py-4 text-center text-muted-foreground text-xs">暂无生成记录</p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
