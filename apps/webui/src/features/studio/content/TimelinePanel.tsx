/**
 * AIGC 轻时间线专业入口；不复制 Shot/Storyboard 项目状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { Clock3, Plus, Trash2, Volume2, VolumeX } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import type {
  AigcProject,
  AigcProjectObject,
  AigcTimelineTrack,
  AigcTimelineTrackInput
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcTimelineComposition,
  useAigcTimelines,
  useCreateAigcTimeline,
  useDeleteAigcTimeline,
  useReplaceAigcTimeline
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

interface TimelinePanelProps {
  project: AigcProject
  object: AigcProjectObject
  lifecycleWritable: boolean
  canCreate: boolean
  canUpdate: boolean
  canDelete: boolean
}

function trackInput(track: AigcTimelineTrack, muted = track.muted): AigcTimelineTrackInput {
  return {
    trackType: track.trackType,
    name: track.name,
    orderNo: track.sortOrder,
    muted,
    locked: track.locked,
    clips: track.clips.map((clip) => ({
      mediaVersionId: clip.mediaVersionId,
      sourceObjectId: clip.sourceObjectId,
      sourceObjectVersionId: clip.sourceObjectVersionId,
      positionMs: clip.positionMs,
      inMs: clip.inMs,
      outMs: clip.outMs,
      propertiesJson: clip.properties ? JSON.stringify(clip.properties) : undefined,
      transitionJson: clip.transition ? JSON.stringify(clip.transition) : undefined,
      volume: clip.volume
    }))
  }
}

export function TimelinePanel({
  project,
  object,
  lifecycleWritable,
  canCreate,
  canUpdate,
  canDelete
}: TimelinePanelProps) {
  const [deleteOpen, setDeleteOpen] = useState(false)
  const { data: timelinePage, isLoading } = useAigcTimelines({
    projectId: object.projectId,
    deliverableObjectId: object.id
  })
  const timelineId = timelinePage?.list.at(0)?.id ?? null
  const { data: composition, isLoading: compositionLoading } =
    useAigcTimelineComposition(timelineId)
  const createTimeline = useCreateAigcTimeline()
  const replaceTimeline = useReplaceAigcTimeline()
  const deleteTimeline = useDeleteAigcTimeline()
  const createEnabled = canCreate && lifecycleWritable
  const updateEnabled = canUpdate && lifecycleWritable
  const deleteEnabled = canDelete && lifecycleWritable

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
        {canCreate ? (
          <Button
            type="button"
            disabled={!createEnabled || createTimeline.isPending}
            onClick={() => {
              if (!createEnabled) return
              createTimeline.mutate(
                {
                  projectId: object.projectId,
                  expectedProjectVersion: project.version,
                  deliverableObjectId: object.id,
                  title: `${object.title || object.stableKey} · 时间线`,
                  durationMs: 0,
                  frameRate: 24,
                  width: 1920,
                  height: 1080
                },
                { onSuccess: () => notify.success("轻时间线已创建") }
              )
            }}
          >
            <Plus /> 创建时间线
          </Button>
        ) : null}
      </div>
    )
  }

  function toggleMuted(trackId: number) {
    if (!updateEnabled || !composition) return
    replaceTimeline.mutate(
      {
        timelineId: composition.id,
        projectId: project.id,
        expectedProjectVersion: project.version,
        expectedVersion: composition.version,
        tracks: composition.tracks.map((track) =>
          trackInput(track, track.id === trackId ? !track.muted : track.muted)
        )
      },
      { onSuccess: () => notify.success("时间线编排已更新") }
    )
  }

  function removeTimeline() {
    if (!deleteEnabled || !composition) return
    deleteTimeline.mutate(
      {
        timelineId: composition.id,
        projectId: project.id,
        expectedProjectVersion: project.version,
        expectedVersion: composition.version
      },
      {
        onSuccess: () => {
          setDeleteOpen(false)
          notify.success("时间线已删除")
        }
      }
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
        <div className="flex items-center gap-2">
          <Badge variant="secondary">{composition.status}</Badge>
          {canDelete ? (
            <Button
              type="button"
              size="xs"
              variant="destructive"
              disabled={!deleteEnabled || deleteTimeline.isPending}
              onClick={() => {
                if (!deleteEnabled) return
                setDeleteOpen(true)
              }}
            >
              <Trash2 />
              删除
            </Button>
          ) : null}
        </div>
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
                {track.name} · {track.trackType} · {track.clips.length} clips
              </span>
              {canUpdate ? (
                <Button
                  type="button"
                  size="xs"
                  variant="ghost"
                  disabled={!updateEnabled || replaceTimeline.isPending || track.locked}
                  onClick={() => toggleMuted(track.id)}
                >
                  {track.muted ? <Volume2 /> : <VolumeX />}
                  {track.muted ? "取消静音" : "静音"}
                </Button>
              ) : null}
            </div>
          ))}
        </div>
      )}

      <Dialog open={deleteOpen && deleteEnabled} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除轻时间线</DialogTitle>
            <DialogDescription>
              将删除当前 Composition 与全部 Track/Clip，请确认权威版本未变化。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDeleteOpen(false)}>
              取消
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={deleteTimeline.isPending}
              onClick={removeTimeline}
            >
              确认删除
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
