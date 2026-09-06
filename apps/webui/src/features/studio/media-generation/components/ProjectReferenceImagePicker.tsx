/**
 * 项目参考图片选择器：仅暴露已采用对象版本关联的图片媒体。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueries } from "@tanstack/react-query"
import { ImageIcon } from "lucide-react"
import { useMemo } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ProjectGraphView } from "@/features/studio/content/graph/ProjectGraphView"
import type { MediaImageAttachment } from "@/features/studio/media-generation/types"
import type { AigcProjectMediaRef, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcProjectGraph, useAigcProjectMediaRefs } from "@/lib/api/rest/ai/aigc"
import { MEDIA_QUERY_KEY, mediaApi } from "@/lib/api/rest/media"

interface ProjectReferenceImagePickerProps {
  open: boolean
  projectId: number
  onOpenChange: (open: boolean) => void
  onSelect: (attachment: MediaImageAttachment) => void
}

const PROJECT_MEDIA_SKELETON_IDS = [
  "media-1",
  "media-2",
  "media-3",
  "media-4",
  "media-5",
  "media-6",
  "media-7",
  "media-8"
] as const

/** 仅保留指向对象当前采用版本的已采用媒体引用。 */
export function filterEligibleProjectMediaRefs(
  refs: AigcProjectMediaRef[],
  objects: AigcProjectObject[]
): AigcProjectMediaRef[] {
  const objectById = new Map(objects.map((object) => [object.id, object]))
  return refs.filter((ref) => {
    if (
      ref.adoptionStatus !== "adopted" ||
      typeof ref.objectId !== "number" ||
      typeof ref.objectVersionId !== "number"
    ) {
      return false
    }
    const adoptedVersionId = objectById.get(ref.objectId)?.adoptedVersionId
    return typeof adoptedVersionId === "number" && ref.objectVersionId === adoptedVersionId
  })
}

/** 选择项目内已采用的图片版本。 */
export function ProjectReferenceImagePicker({
  open,
  projectId,
  onOpenChange,
  onSelect
}: ProjectReferenceImagePickerProps) {
  const { data: graph, isLoading: graphLoading } = useAigcProjectGraph(open ? projectId : null)
  const { data: refs = [], isLoading: refsLoading } = useAigcProjectMediaRefs(
    open ? projectId : null
  )
  const eligibleRefs = useMemo(
    () => (graph ? filterEligibleProjectMediaRefs(refs, graph.objects) : []),
    [graph, refs]
  )
  const mediaQueries = useQueries({
    queries: eligibleRefs.map((ref) => ({
      queryKey: [...MEDIA_QUERY_KEY, "version", ref.mediaVersionId] as const,
      queryFn: () => mediaApi.getByVersionId(ref.mediaVersionId),
      enabled: open
    }))
  })
  const items = eligibleRefs.flatMap((ref, index) => {
    const media = mediaQueries[index]?.data
    if (!media || media.mediaType !== "IMAGE" || ref.objectId === undefined) return []
    const version = media.currentVersion
    return [
      {
        ref,
        objectId: ref.objectId,
        attachment: {
          fileId: version.fileId,
          url: version.url,
          previewSrc: version.thumbnailUrl ?? version.url,
          name: media.name,
          source: "PROJECT" as const,
          mediaVersionId: version.id,
          projectObjectId: ref.objectId
        }
      }
    ]
  })
  const selectableObjectIds = [...new Set(items.map((item) => item.objectId))]
  const isLoading = graphLoading || refsLoading || mediaQueries.some((query) => query.isLoading)

  function select(attachment: MediaImageAttachment) {
    onSelect(attachment)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[min(82vh,760px)] max-w-5xl flex-col">
        <DialogHeader>
          <DialogTitle>选择项目素材</DialogTitle>
          <DialogDescription>仅展示已采用对象版本关联的图片素材。</DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="grid" className="min-h-0 flex-1">
          <TabsList>
            <TabsTrigger value="grid">素材网格</TabsTrigger>
            <TabsTrigger value="graph">项目图谱</TabsTrigger>
          </TabsList>
          <TabsContent value="grid" className="min-h-0 overflow-y-auto">
            {isLoading ? (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {PROJECT_MEDIA_SKELETON_IDS.map((id) => (
                  <Skeleton key={id} className="aspect-square" />
                ))}
              </div>
            ) : items.length === 0 ? (
              <Empty className="min-h-64">
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <ImageIcon />
                  </EmptyMedia>
                  <EmptyTitle>暂无可用图片</EmptyTitle>
                  <EmptyDescription>项目中尚无已采用版本关联的图片素材。</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {items.map(({ ref, attachment }) => (
                  <button
                    key={ref.id}
                    type="button"
                    className="group flex min-w-0 flex-col gap-2 rounded-xl border bg-card p-2 text-left transition-colors hover:border-primary/50 hover:bg-accent"
                    onClick={() => select(attachment)}
                  >
                    {/* biome-ignore lint/performance/noImgElement: 媒体 URL 由后端动态签名 */}
                    <img
                      src={attachment.previewSrc}
                      alt={attachment.name}
                      className="aspect-square w-full rounded-lg object-cover"
                    />
                    <span className="truncate font-medium text-sm">{attachment.name}</span>
                    <span className="truncate text-muted-foreground text-xs">
                      对象 #{attachment.projectObjectId} · {ref.role}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </TabsContent>
          <TabsContent value="graph" className="min-h-0">
            {graph ? (
              <ProjectGraphView
                graph={graph}
                selectionMode
                selectableObjectIds={selectableObjectIds}
                onSelectObject={(objectId) => {
                  const item = items.find((candidate) => candidate.objectId === objectId)
                  if (item) select(item.attachment)
                }}
              />
            ) : (
              <Skeleton className="h-full min-h-[420px] w-full" />
            )}
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
