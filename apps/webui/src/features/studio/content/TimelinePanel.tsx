/**
 * AIGC 轻时间线专业入口；不复制 Shot/Storyboard 项目状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { Clock3, Plus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import {
  useAigcTimelineComposition,
  useAigcTimelines,
  useCreateAigcTimeline
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

interface TimelinePanelProps {
  object: AigcProjectObject
  disabled?: boolean
}

export function TimelinePanel({ object, disabled = false }: TimelinePanelProps) {
  const { data: timelinePage, isLoading } = useAigcTimelines({
    projectId: object.projectId,
    deliverableObjectId: object.id
  })
  const timelineId = timelinePage?.list.at(0)?.id ?? null
  const { data: composition, isLoading: compositionLoading } =
    useAigcTimelineComposition(timelineId)
  const createTimeline = useCreateAigcTimeline()

  if (isLoading || compositionLoading) return <Skeleton className="h-40 w-full" />

  if (!composition) {
    return (
      <div className="flex min-h-48 flex-col items-center justify-center gap-3 rounded-xl border border-dashed p-6 text-center">
        <Clock3 className="text-muted-foreground" />
        <div>
          <p className="font-medium">尚未创建轻时间线</p>
          <p className="text-muted-foreground text-sm">
            创建后从 Track/Clip 读取时间关系，不写回 Shot 或 Storyboard 状态。
          </p>
        </div>
        <Button
          type="button"
          disabled={disabled || createTimeline.isPending}
          onClick={() =>
            createTimeline.mutate(
              {
                projectId: object.projectId,
                deliverableObjectId: object.id,
                title: `${object.title || object.objectKey} · 时间线`,
                durationMs: 0,
                frameRate: 24,
                width: 1920,
                height: 1080
              },
              { onSuccess: () => notify.success("轻时间线已创建") }
            )
          }
        >
          <Plus /> 创建时间线
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-medium">{composition.title}</p>
          <p className="text-muted-foreground text-sm">
            {composition.width}×{composition.height} · {composition.fps} fps ·{" "}
            {composition.durationMs} ms
          </p>
        </div>
        <Badge variant="secondary">{composition.status}</Badge>
      </div>
      {composition.tracks.length === 0 ? (
        <p className="text-muted-foreground text-sm">
          时间线尚无 Track；专业编辑器接入后通过整体替换命令维护。
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {composition.tracks.map((track) => (
            <div
              key={track.id}
              className="flex items-center justify-between gap-3 rounded-lg bg-muted/40 px-3 py-2 text-sm"
            >
              <span>
                {track.name} · {track.trackType}
              </span>
              <span className="text-muted-foreground">{track.clips.length} clips</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
