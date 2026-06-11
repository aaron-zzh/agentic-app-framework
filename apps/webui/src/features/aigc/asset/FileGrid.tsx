/**
 * 文件区素材网格——接入真实 API，素材按组聚合展示
 * @author AaronZZH & Kiro
 */

"use client"

import Image from "next/image"
import { useEffect, useMemo, useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { useMediaAssets } from "@/lib/queries/use-media-assets"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "../store"
import type { MediaAssetVO } from "../types"
import { AssetGroupCard } from "./AssetGroupCard"
import { DraggableAssetCard } from "./DraggableAssetCard"

/** zoom=100 时单列基准宽度（px） */
const BASE_COL_WIDTH = 160

/** 统一的生成/上传动画蒙版 */
function PendingOverlay({ label }: { label: string }) {
  return (
    <div className="absolute inset-0 overflow-hidden rounded-[6px]">
      <div
        className="absolute inset-0"
        style={{
          background: "linear-gradient(135deg, #7c3aed, #db2777, #6366f1, #0ea5e9, #7c3aed)",
          backgroundSize: "300% 300%",
          animation: "gradientBreath 3s ease infinite",
          opacity: 0.7
        }}
      />
      <div
        className="absolute inset-0 animate-[shimmerDiag_4s_ease-in-out_infinite] opacity-0"
        style={{
          background:
            "linear-gradient(135deg, transparent 20%, rgba(255,255,255,0.25) 50%, transparent 80%)",
          backgroundSize: "400% 400%"
        }}
      />
      <div className="absolute inset-x-0 bottom-2 flex justify-center">
        <span className="line-clamp-1 rounded-full bg-black/35 px-2 py-0.5 text-[10px] text-white/90 backdrop-blur-sm">
          {label}
        </span>
      </div>
    </div>
  )
}

function PendingTaskCard({
  task
}: {
  task: {
    id: number
    prompt: string
    type: string
    ossUrl?: string
    error?: string
    asset?: import("../types").MediaAssetVO
  }
}) {
  const [loaded, setLoaded] = useState(false)

  if (task.asset) {
    const uploading = task.asset.url.startsWith("blob:")
    return (
      <div className="relative">
        <DraggableAssetCard asset={task.asset} />
        {uploading && (
          <div className="pointer-events-none absolute inset-0">
            <PendingOverlay label="上传中…" />
          </div>
        )}
      </div>
    )
  }
  return (
    <div className="relative aspect-square overflow-hidden rounded-[6px] border border-border/50">
      {task.error ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-1 bg-red-500/30">
          <span className="text-2xl text-red-500">✕</span>
          <span className="line-clamp-2 px-2 text-center font-medium text-[10px] text-red-500">
            生成失败
          </span>
        </div>
      ) : (
        <>
          {!loaded && <PendingOverlay label={task.prompt.slice(0, 16)} />}
          {task.ossUrl && (
            <Image
              src={task.ossUrl}
              alt={task.prompt}
              fill
              className={cn(
                "object-cover transition-opacity duration-500",
                loaded ? "opacity-100" : "opacity-0"
              )}
              onLoad={() => setLoaded(true)}
            />
          )}
        </>
      )}
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
}

const EMPTY_LIST: MediaAssetVO[] = []
const QUERY_PARAMS = { page: 0, pageSize: 20 }

export function FileGrid({ filterUnassigned = false }: FileGridProps) {
  const { data, isLoading } = useMediaAssets(QUERY_PARAMS)
  const storyboardAssets = useAigcStore((s) => s.storyboardAssets)
  const setPreviewList = useAigcStore((s) => s.setPreviewList)
  const pendingTasks = useAigcStore((s) => s.pendingTasks)
  const fileZoom = useAigcStore((s) => s.fileZoom)
  const colWidth = Math.round((BASE_COL_WIDTH * fileZoom) / 100)

  const assignedIds = useMemo(() => new Set(storyboardAssets.map((a) => a.id)), [storyboardAssets])
  const list = data?.list ?? EMPTY_LIST
  const filtered = useMemo(
    () => (filterUnassigned ? list.filter((a) => !assignedIds.has(a.id)) : list),
    [filterUnassigned, list, assignedIds]
  )

  useEffect(() => {
    setPreviewList(filtered)
  }, [filtered, setPreviewList])

  const { groups, ungrouped } = useMemo(() => {
    const groupMap = new Map<number, { id: number; name: string; assets: MediaAssetVO[] }>()
    const ungrouped: MediaAssetVO[] = []
    for (const asset of filtered) {
      if (asset.groupId != null) {
        if (!groupMap.has(asset.groupId)) {
          groupMap.set(asset.groupId, {
            id: asset.groupId,
            name: asset.groupName ?? `组 ${asset.groupId}`,
            assets: []
          })
        }
        groupMap.get(asset.groupId)?.assets.push(asset)
      } else {
        ungrouped.push(asset)
      }
    }
    return { groups: Array.from(groupMap.values()), ungrouped }
  }, [filtered])

  if (isLoading) return <FileGridSkeleton />

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
      {groups.map((g) => (
        <AssetGroupCard key={g.id} groupId={g.id} groupName={g.name} assets={g.assets} colWidth={colWidth} />
      ))}
      {ungrouped.map((asset) => (
        <DraggableAssetCard key={asset.id} asset={asset} />
      ))}
      {pendingTasks.map((task) => (
        <PendingTaskCard key={`pending-${task.id}`} task={task} />
      ))}
    </div>
  )
}
