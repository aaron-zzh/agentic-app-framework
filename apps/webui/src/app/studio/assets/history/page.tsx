/**
 * /studio/assets/history——AIGC 任务历史，展示所有状态，失败可重试
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import {
  CheckCircle2,
  Image as ImageIcon,
  Loader2,
  RefreshCw,
  Trash2,
  Video,
  XCircle
} from "lucide-react"
import { toast } from "sonner"
import { GlassCard } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { type PageResult, request } from "@/lib/api/rest/entity/crud"
import { useCancelAigcTask } from "@/lib/queries/use-cancel-aigc-task"
import { useGenerateImage, useGenerateVideo } from "@/lib/queries/use-image-generation"

// ─── 类型 ───────────────────────────────────────────────────────────────────

interface AigcTaskVO {
  id: number
  type: string
  status: string
  prompt: string
  model: string
  provider: string
  ossUrl: string | null
  errorMsg: string | null
  createTime: string
}

// ─── Hook ───────────────────────────────────────────────────────────────────

function useAigcTaskHistory(page = 1, size = 40) {
  return useQuery({
    queryKey: ["aigc", "tasks", "history", page] as const,
    queryFn: () => request<PageResult<AigcTaskVO>>(`/aigc/tasks?pageNo=${page}&pageSize=${size}`)
  })
}

// ─── 任务卡片 ────────────────────────────────────────────────────────────────

function TaskCard({ task }: { task: AigcTaskVO }) {
  const retryImage = useGenerateImage()
  const retryVideo = useGenerateVideo()
  const cancelTask = useCancelAigcTask()

  const handleRetry = async () => {
    try {
      if (task.type.includes("VIDEO")) {
        await retryVideo.mutateAsync({ prompt: task.prompt, model: task.model })
      } else {
        await retryImage.mutateAsync({ prompt: task.prompt, model: task.model })
      }
      toast.success("已重新提交任务")
    } catch {
      toast.error("重试失败")
    }
  }

  const handleDelete = () => {
    cancelTask.mutate(task.id, {
      onError: () => toast.error("删除失败")
    })
  }

  const isVideo = task.type.includes("VIDEO")
  const isFail = task.status === "FAIL"
  const isPending = task.status === "PENDING" || task.status === "RUNNING"

  return (
    <GlassCard glow="none" className="overflow-hidden">
      {/* 缩略图 */}
      <div className="relative aspect-square bg-foreground/[0.04]">
        {task.ossUrl && !isFail ? (
          isVideo ? (
            <video
              src={task.ossUrl}
              className="size-full object-cover"
              muted
              playsInline
              onMouseOver={(e) => e.currentTarget.play()}
              onFocus={(e) => e.currentTarget.play()}
              onMouseOut={(e) => {
                e.currentTarget.pause()
                e.currentTarget.currentTime = 0
              }}
              onBlur={(e) => {
                e.currentTarget.pause()
                e.currentTarget.currentTime = 0
              }}
            />
          ) : (
            // biome-ignore lint/performance/noImgElement: 缩略图
            <img src={task.ossUrl} alt={task.prompt} className="size-full object-cover" />
          )
        ) : (
          <div className="flex size-full flex-col items-center justify-center gap-1.5 text-muted-foreground/40">
            {isFail ? (
              <>
                <XCircle className="size-8 text-rose-400/70" />
                <span className="text-rose-400/70 text-xs">生成失败</span>
              </>
            ) : isPending ? (
              <Loader2 className="size-8 animate-spin" />
            ) : isVideo ? (
              <Video className="size-8" />
            ) : (
              <ImageIcon className="size-8" />
            )}
          </div>
        )}

        {/* 成功状态角标 */}
        {!isFail && task.status === "SUCCESS" && (
          <div className="absolute top-1.5 right-1.5">
            <CheckCircle2 className="size-4 text-emerald-400" />
          </div>
        )}

        {/* 失败：右上角删除按钮 */}
        {isFail && (
          <button
            type="button"
            onClick={handleDelete}
            disabled={cancelTask.isPending}
            className="absolute top-1.5 right-1.5 text-muted-foreground/60 hover:text-muted-foreground"
          >
            <Trash2 className="size-4" />
          </button>
        )}
      </div>

      {/* 信息 */}
      <div className="space-y-1 p-2">
        <p className="line-clamp-2 text-xs leading-tight">{task.prompt || "—"}</p>
        {isFail && (
          <div className="space-y-1">
            {task.errorMsg && (
              <p className="line-clamp-1 text-[10px] text-rose-400">{task.errorMsg}</p>
            )}
            <Button
              size="sm"
              variant="outline"
              className="h-6 w-full gap-1 text-[10px]"
              onClick={handleRetry}
              disabled={retryImage.isPending || retryVideo.isPending}
            >
              <RefreshCw className="size-3" />
              重试
            </Button>
          </div>
        )}
      </div>
    </GlassCard>
  )
}

// ─── 页面 ────────────────────────────────────────────────────────────────────

export default function StudioAssetsHistoryPage() {
  const { data, isLoading } = useAigcTaskHistory()
  const items = data?.list ?? []

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <header>
        <h1 className="font-semibold text-xl">生成历史</h1>
        <p className="mt-1 text-muted-foreground text-sm">所有生成任务，失败任务可直接重试</p>
      </header>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="aspect-square rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="py-20 text-center text-muted-foreground text-sm">暂无生成记录</div>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </div>
      )}
    </div>
  )
}
