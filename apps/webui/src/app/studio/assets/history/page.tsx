/**
 * /studio/assets/history——AIGC 任务历史，展示所有状态，失败可重试
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import {
  Image as ImageIcon,
  Loader2,
  MoreHorizontal,
  RefreshCw,
  Trash2,
  Video,
  XCircle
} from "lucide-react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import VideoPlugin from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { GlassCard } from "@/components/studio"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Skeleton } from "@/components/ui/skeleton"
import { type PageResult, request } from "@/lib/api/rest/entity/crud"
import { useCancelAigcTask } from "@/lib/queries/use-cancel-aigc-task"

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
  params: string | null
  createTime: string
}

// ─── Hook ───────────────────────────────────────────────────────────────────

function useAigcTaskHistory(page = 1, size = 40) {
  return useQuery({
    queryKey: ["aigc", "tasks", "history", page] as const,
    queryFn: () => request<PageResult<AigcTaskVO>>(`/aigc/tasks?pageNo=${page}&pageSize=${size}`)
  })
}

// ─── 任务类型→创作页路由 ──────────────────────────────────────────────────────

function getCreatePath(type: string): string {
  if (type.includes("VIDEO")) return "/studio/create/video"
  if (type === "MUSIC") return "/studio/create/music"
  if (type === "VOICE") return "/studio/create/voice"
  if (type === "MODEL_3D") return "/studio/create/tools/3d"
  return "/studio/create/image"
}

function writeRegenerateSession(task: AigcTaskVO) {
  const p = task.params
    ? (() => {
        try {
          return JSON.parse(task.params)
        } catch {
          return {}
        }
      })()
    : {}
  sessionStorage.setItem(
    "aaf:regenerate",
    JSON.stringify({
      prompt: task.prompt,
      model: task.model,
      ...p
    })
  )
}

// ─── 任务卡片 ────────────────────────────────────────────────────────────────

function TaskCard({ task, onPreview }: { task: AigcTaskVO; onPreview: (url: string) => void }) {
  const router = useRouter()
  const cancelTask = useCancelAigcTask()

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
      {/* biome-ignore lint/a11y/noStaticElementInteractions: 内部含 <button>，不能嵌套 <button> */}
      <div
        className="relative aspect-square bg-foreground/4"
        role={task.ossUrl && !isFail ? "button" : undefined}
        tabIndex={task.ossUrl && !isFail ? 0 : undefined}
        onClick={(e) => {
          if ((e.target as HTMLElement).closest("button,[role=menuitem]")) return
          task.ossUrl && !isFail && onPreview(task.ossUrl)
        }}
        onKeyDown={(e) => e.key === "Enter" && task.ossUrl && !isFail && onPreview(task.ossUrl)}
        style={task.ossUrl && !isFail ? { cursor: "zoom-in" } : undefined}
      >
        {task.ossUrl && !isFail ? (
          isVideo ? (
            <video
              src={task.ossUrl}
              className="pointer-events-none size-full object-cover"
              muted
              playsInline
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

        {/* 成功/失败：三点菜单 */}
        {(task.status === "SUCCESS" || isFail) && (
          <div className="absolute top-1 right-1">
            <DropdownMenu>
              <DropdownMenuTrigger
                className="flex size-6 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur-sm hover:bg-black/60"
                onClick={(e) => e.stopPropagation()}
              >
                <MoreHorizontal className="size-3.5" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  onClick={() => {
                    writeRegenerateSession(task)
                    router.push(getCreatePath(task.type))
                  }}
                >
                  <RefreshCw className="mr-2 size-3.5" />
                  重新生成
                </DropdownMenuItem>
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={(e) => {
                    e.stopPropagation()
                    handleDelete()
                  }}
                  disabled={cancelTask.isPending}
                >
                  <Trash2 className="mr-2 size-3.5" />
                  删除
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </div>

      {/* 信息 */}
      <div className="space-y-1 p-2">
        <p className="line-clamp-2 text-xs leading-tight">{task.prompt || "—"}</p>
        {isFail && task.errorMsg && (
          <p className="line-clamp-1 text-[10px] text-rose-400">{task.errorMsg}</p>
        )}
      </div>
    </GlassCard>
  )
}

// ─── 页面 ────────────────────────────────────────────────────────────────────

export default function StudioAssetsHistoryPage() {
  const { data, isLoading } = useAigcTaskHistory()
  const items = data?.list ?? []

  const slides = items
    .filter((t) => t.status === "SUCCESS" && t.ossUrl)
    .map((t) =>
      t.type.includes("VIDEO")
        ? { type: "video" as const, sources: [{ src: t.ossUrl as string, type: "video/mp4" }] }
        : { src: t.ossUrl as string }
    )

  const { open, index, onOpen, onClose } = useLightbox(slides)

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
            <TaskCard key={task.id} task={task} onPreview={onOpen} />
          ))}
        </div>
      )}

      <Lightbox open={open} index={index} slides={slides} close={onClose} plugins={[VideoPlugin]} />
    </div>
  )
}
