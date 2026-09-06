/**
 * 素材区素材网格——接入真实 API，素材按组聚合展示
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { PendingOverlay } from "@/components/animate/PendingOverlay"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcTaskEvent } from "@/lib/api/rest/ai/aigc-task"
import { type PageResult, request } from "@/lib/api/rest/entity"
import { useMediaList } from "@/lib/api/rest/media"
import { useAigcStore } from "../store"
import { DraggableAssetCard } from "./DraggableAssetCard"

/** zoom=100 时单列基准宽度（px） */
const BASE_COL_WIDTH = 160

function useActiveAigcTasks(projectId?: number | null) {
  return useQuery({
    queryKey: ["aigc.task", "active", projectId ?? null] as const,
    queryFn: () =>
      request<PageResult<AigcTaskEvent>>(
        `/aigc/tasks?pageNo=1&pageSize=40${projectId ? `&projectId=${projectId}` : ""}`
      )
  })
}

function PendingTaskCard({ task }: { task: AigcTaskEvent }) {
  return (
    <div className="relative aspect-square overflow-hidden rounded-[6px] border border-border/50">
      <PendingOverlay label={(task.prompt ?? task.type).slice(0, 16)} showProgress />
    </div>
  )
}

function FileGridSkeleton() {
  const fileZoom = useAigcStore((s) => s.fileZoom)
  const colWidth = Math.round((160 * fileZoom) / 100)
  return (
    <div
      className="grid gap-2 p-3"
      style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${colWidth}px, 1fr))` }}
    >
      {Array.from({ length: 10 }).map((_, i) => (
        <div key={`skeleton-${i}`} className="overflow-hidden rounded-lg border border-border/50">
          <Skeleton className="aspect-square w-full" />
          <div className="px-2 py-1.5">
            <Skeleton className="h-3 w-3/4" />
          </div>
        </div>
      ))}
    </div>
  )
}

interface FileGridProps {
  filterUnassigned?: boolean
  projectId?: number | null
}

export function FileGrid({ filterUnassigned = false, projectId }: FileGridProps) {
  const queryParams = projectId
    ? { pageNo: 1, pageSize: 20, projectId }
    : { pageNo: 1, pageSize: 20 }
  const { data, isLoading } = useMediaList(queryParams)
  const { data: taskPage, isLoading: tasksLoading } = useActiveAigcTasks(projectId)
  const storyboardMediaIds = useAigcStore((state) => state.storyboardMediaIds)
  const setPreviewMediaIds = useAigcStore((state) => state.setPreviewMediaIds)
  const fileZoom = useAigcStore((state) => state.fileZoom)
  const colWidth = Math.round((BASE_COL_WIDTH * fileZoom) / 100)
  const pendingTasks = (taskPage?.list ?? []).filter(
    (task) => task.status === "PENDING" || task.status === "RUNNING"
  )

  const assignedIds = useMemo(() => new Set(storyboardMediaIds), [storyboardMediaIds])
  const list = useMemo(() => data?.list ?? [], [data?.list])
  const filtered = useMemo(
    () => (filterUnassigned ? list.filter((media) => !assignedIds.has(media.id)) : list),
    [filterUnassigned, list, assignedIds]
  )

  useEffect(() => {
    setPreviewMediaIds(filtered.map((media) => media.id))
  }, [filtered, setPreviewMediaIds])

  if (isLoading || tasksLoading) return <FileGridSkeleton />

  if (filtered.length === 0 && pendingTasks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 p-8 text-center">
        <p className="text-muted-foreground text-sm">暂无素材</p>
        <p className="text-muted-foreground text-xs">生成或上传素材后将在此展示</p>
      </div>
    )
  }

  return (
    <div
      className="grid gap-2 p-3"
      style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${colWidth}px, 1fr))` }}
    >
      {filtered.map((media) => (
        <DraggableAssetCard key={media.id} media={media} />
      ))}
      {pendingTasks.map((task) => (
        <PendingTaskCard key={`pending-${task.id}`} task={task} />
      ))}
    </div>
  )
}
