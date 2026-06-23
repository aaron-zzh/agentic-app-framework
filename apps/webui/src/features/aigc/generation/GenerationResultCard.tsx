/**
 * GenerationResultCard——生成结果卡片
 *
 * 在 /studio/create/image 和 /studio/create/video 提交成功后展示。
 * 右上角"→保存到项目"按钮触发 SaveToProjectDialog。
 * M6: PENDING/RUNNING 状态显示"取消"按钮
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, FolderInput, Loader2, X, XCircle } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { GlassCard } from "@/components/studio/GlassCard"
import { GlowButton } from "@/components/studio/GlowButton"
import type { AigcTaskEvent } from "@/lib/hooks/use-aigc-task-stream"
import { useCancelAigcTask } from "@/lib/queries/use-cancel-aigc-task"
import type { SaveFromGenerationParams } from "@/lib/queries/use-image-generation"
import { SaveToProjectDialog } from "./SaveToProjectDialog"

interface GenerationResultCardProps {
  tasks: AigcTaskEvent[]
  mediaType: "IMAGE" | "VIDEO"
}

export function GenerationResultCard({ tasks, mediaType }: GenerationResultCardProps) {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedAsset, setSelectedAsset] = useState<Omit<
    SaveFromGenerationParams,
    "projectId"
  > | null>(null)
  const { mutate: cancelTask, isPending: cancelling } = useCancelAigcTask()

  if (tasks.length === 0) return null

  const handleSaveToProject = (task: AigcTaskEvent) => {
    setSelectedAsset({
      url: task.ossUrl ?? task.resultUrl ?? "",
      name: task.prompt?.slice(0, 40) ?? "生成作品",
      type: mediaType,
      thumbnailUrl: task.ossUrl ?? task.resultUrl ?? undefined
    })
    setDialogOpen(true)
  }

  const handleCancel = (taskId: number) => {
    cancelTask(taskId, {
      onSuccess: () => toast.success("任务已取消"),
      onError: () => toast.error("取消失败，请稍后重试")
    })
  }

  return (
    <>
      <GlassCard glow="violet" className="overflow-hidden">
        <div className="border-foreground/[0.06] border-b px-4 py-3">
          <p className="font-medium text-sm">生成结果</p>
        </div>
        <div className="divide-y divide-foreground/[0.04]">
          {tasks.map((task) => (
            <div key={task.id} className="flex items-center gap-3 px-4 py-3">
              <TaskStatusIcon status={task.status} />

              {task.status === "SUCCESS" && task.ossUrl ? (
                // biome-ignore lint/performance/noImgElement: 生成缩略图，无尺寸信息无法用 next/image
                <img
                  src={task.ossUrl}
                  alt={task.prompt ?? "生成结果"}
                  className="size-12 shrink-0 rounded-lg object-cover"
                />
              ) : (
                <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-foreground/[0.04] text-muted-foreground/30 text-xs">
                  {mediaType === "IMAGE" ? "图" : "视"}
                </div>
              )}

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">
                  {task.prompt?.slice(0, 50) ?? `任务 #${task.id}`}
                </p>
                <p className="mt-0.5 text-muted-foreground text-xs">
                  {STATUS_LABEL[task.status]}
                  {task.errorMsg && ` · ${task.errorMsg.slice(0, 30)}`}
                </p>
              </div>

              {/* 取消按钮：PENDING/RUNNING 时显示 */}
              {(task.status === "PENDING" || task.status === "RUNNING") && (
                <GlowButton
                  tone="ghost"
                  size="sm"
                  disabled={cancelling}
                  onClick={() => handleCancel(task.id)}
                  className="shrink-0 text-muted-foreground text-xs hover:text-destructive"
                >
                  <X className="size-3.5" />
                  取消
                </GlowButton>
              )}

              {task.status === "SUCCESS" && (
                <GlowButton
                  tone="ghost"
                  size="sm"
                  onClick={() => handleSaveToProject(task)}
                  className="shrink-0 text-xs"
                >
                  <FolderInput className="size-3.5" />
                  保存到项目
                </GlowButton>
              )}
            </div>
          ))}
        </div>
      </GlassCard>

      {selectedAsset && (
        <SaveToProjectDialog open={dialogOpen} onOpenChange={setDialogOpen} asset={selectedAsset} />
      )}
    </>
  )
}

const STATUS_LABEL: Record<AigcTaskEvent["status"], string> = {
  PENDING: "排队中",
  RUNNING: "生成中",
  SUCCESS: "已完成",
  FAIL: "生成失败，已自动退还积分"
}

function TaskStatusIcon({ status }: { status: AigcTaskEvent["status"] }) {
  if (status === "SUCCESS") return <CheckCircle2 className="size-4 shrink-0 text-emerald-400" />
  if (status === "FAIL") return <XCircle className="size-4 shrink-0 text-destructive" />
  return <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
}
