/**
 * Content Studio 项目结构 / 图谱双视图工作台。
 * @author AaronZZH & Kiro
 */

"use client"

import { FolderKanban } from "lucide-react"
import Link from "next/link"
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation"
import { useEffect, useState } from "react"
import { GlowButton } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import {
  ContentActionBar,
  type ContentCanvasMode,
  ContentCanvasSheet,
  getObjectStage,
  PROJECT_GRAPH_STAGES,
  type ProjectGraphStage,
  ProjectGraphView,
  ProjectStructureView,
  ProjectWorkbenchHeader,
  type ProjectWorkbenchView,
  useProjectGraphViewState
} from "@/features/studio/content"
import {
  type ContentProjectObjectVO,
  useContentProject,
  useContentProjectGraph,
  useContentProjectSummary
} from "@/lib/api/rest/content"
import { useChatterStore } from "@/lib/store/chatter-store"

interface CanvasSession {
  mode: ContentCanvasMode
  object: ContentProjectObjectVO
}

function parseFocus(value: string | null): number | undefined {
  if (!value) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function isStage(value: string | null): value is ProjectGraphStage {
  return PROJECT_GRAPH_STAGES.some((stage) => stage.key === value)
}

export default function StudioProjectDetailPage() {
  const params = useParams<{ id: string }>()
  const pathname = usePathname()
  const router = useRouter()
  const searchParams = useSearchParams()
  const projectId = Number(params.id)
  const validProjectId = Number.isFinite(projectId) && projectId > 0 ? projectId : null
  const view: ProjectWorkbenchView = searchParams.get("view") === "graph" ? "graph" : "structure"
  const focusObjectId = parseFocus(searchParams.get("focus"))
  const requestedStage = searchParams.get("stage")
  const [canvasSession, setCanvasSession] = useState<CanvasSession | null>(null)
  const setGraphFocus = useProjectGraphViewState((state) => state.setFocusObjectId)
  const { data: project, isLoading: projectLoading } = useContentProject(validProjectId)
  const { data: graph, isLoading: graphLoading } = useContentProjectGraph(validProjectId)
  const { data: summary } = useContentProjectSummary(validProjectId)

  const setOpen = useChatterStore((state) => state.setOpen)
  const setMode = useChatterStore((state) => state.setMode)
  const setLayoutOverride = useChatterStore((state) => state.setLayoutOverride)
  const chatterOpen = useChatterStore((state) => state.open)
  const chatterMode = useChatterStore((state) => state.mode)

  const focusedObject = graph?.objects.find((object) => object.id === focusObjectId)
  const activeStage: ProjectGraphStage = isStage(requestedStage)
    ? requestedStage
    : focusedObject
      ? getObjectStage(focusedObject)
      : "planning"

  useEffect(() => {
    setGraphFocus(focusObjectId ?? null)
  }, [focusObjectId, setGraphFocus])

  function updateUrl(values: Record<string, string | number | null>) {
    const next = new URLSearchParams(searchParams.toString())
    for (const [key, value] of Object.entries(values)) {
      if (value === null) next.delete(key)
      else next.set(key, String(value))
    }
    const query = next.toString()
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
  }

  function handleToggleChat() {
    if (chatterOpen && chatterMode === "panel") {
      setOpen(false)
      return
    }
    setMode("panel")
    setLayoutOverride("panel")
    setOpen(true)
  }

  function handleFocusObject(id: number) {
    const object = graph?.objects.find((item) => item.id === id)
    setGraphFocus(id)
    updateUrl({
      focus: id,
      ...(view === "structure" && object ? { stage: getObjectStage(object) } : {})
    })
  }

  function handleOpenCanvas(id: number, mode: ContentCanvasMode) {
    const object = graph?.objects.find((item) => item.id === id)
    if (!object) return
    handleFocusObject(id)
    setCanvasSession({ mode, object })
  }

  if (projectLoading || graphLoading) {
    return (
      <div className="flex h-full flex-col gap-3 p-6">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="min-h-[520px] flex-1" />
      </div>
    )
  }

  if (!project || !graph) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-muted-foreground">
        <FolderKanban className="size-12 opacity-30" />
        <p className="text-sm">项目不存在或已删除</p>
        <GlowButton
          nativeButton={false}
          render={<Link href="/studio/projects" />}
          tone="ghost"
          size="sm"
        >
          返回项目列表
        </GlowButton>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <ProjectWorkbenchHeader
        project={project}
        view={view}
        onViewChange={(nextView) => updateUrl({ view: nextView })}
        onToggleChat={handleToggleChat}
        chatOpen={chatterOpen && chatterMode === "panel"}
      />

      <ContentActionBar project={project} focusedObject={focusedObject} />

      <main className="min-h-0 flex-1 overflow-auto p-4 sm:p-6">
        {view === "graph" ? (
          <ProjectGraphView
            graph={graph}
            focusObjectId={focusObjectId}
            onFocusObject={handleFocusObject}
            onOpenCanvas={(id) => handleOpenCanvas(id, "canvas")}
            onAnnotateImage={(id) => handleOpenCanvas(id, "annotation")}
          />
        ) : (
          <ProjectStructureView
            graph={graph}
            activeStage={activeStage}
            focusObjectId={focusObjectId}
            onStageChange={(stage) => updateUrl({ stage })}
            onFocusObject={handleFocusObject}
            onOpenCanvas={(id) => handleOpenCanvas(id, "canvas")}
            onAnnotateImage={(id) => handleOpenCanvas(id, "annotation")}
          />
        )}
      </main>

      <footer className="flex flex-wrap items-center gap-x-5 gap-y-1 border-t bg-background/80 px-4 py-2 text-muted-foreground text-xs sm:px-6">
        <span>图谱 revision {graph.graphRevision}</span>
        <span>{summary?.objectCount ?? graph.objects.length} 个对象</span>
        <span>{summary?.pendingConfirmCount ?? 0} 项待确认</span>
        <span>{summary?.blockedCount ?? 0} 项被阻断</span>
        <span>{summary?.executionRunningCount ?? 0} 个任务执行中</span>
        <span>费用 {summary?.costUsed ?? project.costUsed}</span>
        {project.status === "archived" ? (
          <span className="text-amber-500">已归档 · 只读</span>
        ) : null}
      </footer>

      <ContentCanvasSheet
        open={canvasSession !== null}
        mode={canvasSession?.mode ?? "canvas"}
        projectId={project.id}
        object={canvasSession?.object ?? null}
        onOpenChange={(open) => {
          if (!open) setCanvasSession(null)
        }}
      />
    </div>
  )
}
