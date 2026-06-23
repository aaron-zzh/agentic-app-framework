/**
 * /studio/projects/[id]——项目工作台
 *
 * 直接复用管理后台 AigcView（元素区+预览区+素材区+生成弹窗）
 * 对话由全局 Chatter panel 嵌入模式提供
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowLeft, FolderKanban, LayoutGrid, MessageSquare, PenSquare } from "lucide-react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { useState } from "react"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { AigcView } from "@/features/aigc/project/AigcView"
import { GlobalDndContext } from "@/features/dnd/GlobalDndContext"
import { ProjectCanvas } from "@/features/studio/projects/ProjectCanvas"
import {
  useAigcProject,
  useAigcProjectSummary
} from "@/lib/queries/use-aigc-projects"
import { useChatterStore } from "@/lib/store/chatter-store"
import { cn } from "@/lib/utils/index"

export default function StudioProjectDetailPage() {
  const params = useParams<{ id: string }>()
  const projectId = Number(params.id)
  const [view, setView] = useState<"workspace" | "canvas">("workspace")

  const setOpen = useChatterStore((s) => s.setOpen)
  const setMode = useChatterStore((s) => s.setMode)
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)
  const chatterOpen = useChatterStore((s) => s.open)
  const chatterMode = useChatterStore((s) => s.mode)

  const handleToggleChat = () => {
    if (chatterOpen && chatterMode === "panel") {
      setOpen(false)
    } else {
      setMode("panel")
      setLayoutOverride("panel")
      setOpen(true)
    }
  }

  const { data: project, isLoading } = useAigcProject(projectId)
  const { data: summary } = useAigcProjectSummary(projectId)

  if (isLoading) {
    return (
      <div className="space-y-3 p-6">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-[60vh] w-full" />
      </div>
    )
  }

  if (!project) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-muted-foreground">
        <FolderKanban className="size-12 opacity-30" />
        <p className="text-sm">项目不存在或已删除</p>
        <Link href="/studio/projects">
          <GlowButton tone="ghost" size="sm">返回项目列表</GlowButton>
        </Link>
      </div>
    )
  }

  return (
    <GlobalDndContext>
      <div className="flex h-full flex-col">
        {/* 项目头 */}
        <header className="flex items-center justify-between gap-3 border-foreground/[0.06] border-b bg-background/40 px-6 py-3 backdrop-blur">
          <div className="flex items-center gap-3">
            <Link
              href="/studio/projects"
              className="flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-foreground/[0.04] hover:text-foreground"
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div className="leading-tight">
              <h1 className="font-semibold text-base">{project.name}</h1>
              <div className="mt-0.5 flex items-center gap-2 text-muted-foreground text-xs">
                <NeonChip tone="violet" size="sm">{project.type}</NeonChip>
                <NeonChip tone="neutral" size="sm" dot>{project.status}</NeonChip>
                {summary && (
                  <span className="text-muted-foreground/80">
                    {summary.contentCount} 内容 · {summary.assetCount} 资产
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* 对话按钮 */}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleToggleChat}
              title="项目对话"
              className={cn(chatterOpen && chatterMode === "panel" && "text-primary")}
            >
              <MessageSquare className="size-4" />
            </Button>

            {/* 视图切换 */}
            <div className="flex gap-1 rounded-lg bg-foreground/[0.04] p-1">
              {(["workspace", "canvas"] as const).map((v) => (
                <button
                  key={v}
                  type="button"
                  onClick={() => setView(v)}
                  className={cn(
                    "flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium text-xs transition-colors",
                    view === v
                      ? "bg-background text-foreground shadow-sm"
                      : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  {v === "workspace" ? (
                    <LayoutGrid className="size-3.5" />
                  ) : (
                    <PenSquare className="size-3.5" />
                  )}
                  {v === "workspace" ? "工作台" : "画布"}
                </button>
              ))}
            </div>
          </div>
        </header>

        {view === "canvas" ? (
          <div className="flex-1">
            <ProjectCanvas projectId={projectId} />
          </div>
        ) : (
          <AigcView projectId={projectId} />
        )}
      </div>
    </GlobalDndContext>
  )
}
