/**
 * 项目对象专业媒体生成对话框：仅展示服务端 allowedActions 允许的动作。
 *
 * @author AaronZZH & Kiro
 */

"use client"

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
import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"
import type {
  MediaGenerationMode,
  MediaProjectTarget
} from "@/features/studio/media-generation/types"
import type { AigcActionOption, AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcProjectActions, useAigcProjectReadiness } from "@/lib/api/rest/ai/aigc"
import { OBJECT_TYPE_LABELS } from "./graph/graph-projection"

interface ProjectObjectGenerationDialogProps {
  open: boolean
  project: AigcProject
  object?: AigcProjectObject
  mutable: boolean
  onOpenChange: (open: boolean) => void
  onSubmitted?: (executionRunId: number) => void
}

function modeForAction(
  actionKey: string
): Extract<MediaGenerationMode, "image" | "video" | "voice"> | null {
  if (actionKey.startsWith("image.")) return "image"
  if (actionKey.startsWith("video.")) return "video"
  if (actionKey.startsWith("audio.")) return "voice"
  return null
}

/** 每种媒体模式在项目域的首选动作 key；voice 模式对应服务端 audio.generate。 */
const PREFERRED_ACTION_KEY_BY_MODE = {
  image: "image.generate",
  video: "video.generate",
  voice: "audio.generate"
} as const

/** 将服务端允许的媒体动作收敛为当前对象的显式项目目标。 */
export function buildProjectMediaTargets(
  actions: AigcActionOption[],
  project: AigcProject,
  object: AigcProjectObject
): Partial<Record<MediaGenerationMode, MediaProjectTarget>> {
  const targets: Partial<Record<MediaGenerationMode, MediaProjectTarget>> = {}
  for (const action of actions) {
    const mode = modeForAction(action.actionKey)
    if (!mode || !action.applicableObjectTypes.includes(object.objectType)) continue
    const existing = targets[mode]
    const preferredActionKey = PREFERRED_ACTION_KEY_BY_MODE[mode]
    if (existing?.actionKey === preferredActionKey) continue
    if (existing && action.actionKey !== preferredActionKey) continue
    targets[mode] = {
      projectId: project.id,
      projectVersion: project.version,
      graphRevision: project.graphRevision,
      objectId: object.id,
      actionKey: action.actionKey
    }
  }
  return targets
}

/** 按当前对象的权威产物合同展示专业媒体生成。 */
export function ProjectObjectGenerationDialog({
  open,
  project,
  object,
  mutable,
  onOpenChange,
  onSubmitted
}: ProjectObjectGenerationDialogProps) {
  const { data: actions = [], isLoading: actionsLoading } = useAigcProjectActions(
    open && mutable ? project.id : null
  )
  const { data: readiness = [], isLoading: readinessLoading } = useAigcProjectReadiness(
    open && mutable ? project.id : null,
    object?.contractRole === "OPTIONAL" && object ? [object.id] : []
  )
  const objectReadiness = readiness.find((item) => item.projectObjectId === object?.id)
  const allowedActionKeys = useMemo(
    () => new Set(objectReadiness?.allowedActions ?? []),
    [objectReadiness?.allowedActions]
  )
  const projectTargets = useMemo(
    () =>
      object
        ? buildProjectMediaTargets(
            actions.filter((action) => allowedActionKeys.has(action.actionKey)),
            project,
            object
          )
        : {},
    [actions, allowedActionKeys, object, project]
  )
  const initialMode = projectTargets.image
    ? "image"
    : projectTargets.video
      ? "video"
      : projectTargets.voice
        ? "voice"
        : "image"
  const hasMediaMode =
    projectTargets.image !== undefined ||
    projectTargets.video !== undefined ||
    projectTargets.voice !== undefined
  const loading = readinessLoading || actionsLoading
  const regenerating = object?.adoptedVersionId !== null && object?.adoptedVersionId !== undefined

  return (
    <Dialog open={open && mutable && object !== undefined} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {regenerating ? "重新生成" : "生成"}
            {object?.title || (object ? OBJECT_TYPE_LABELS[object.objectType] : "项目对象")}
          </DialogTitle>
          <DialogDescription>
            {regenerating
              ? `基于已采用版本 #${object?.adoptedVersionId} 重新生成；新候选不会覆盖当前采用版本。`
              : "首次生成会创建新的 ExecutionRun 和候选版本，需在对象详情中确认采用。"}
          </DialogDescription>
        </DialogHeader>
        {loading ? (
          <Skeleton className="h-52 w-full" />
        ) : hasMediaMode && object ? (
          <MediaGenerationWorkspace
            key={`${object.id}-${actions.map((action) => action.actionKey).join("-")}`}
            initialMode={initialMode}
            projectTargets={projectTargets}
            onExecutionSubmitted={(executionRunId) => {
              onSubmitted?.(executionRunId)
              onOpenChange(false)
            }}
          />
        ) : (
          <Empty className="min-h-52">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <ImageIcon />
              </EmptyMedia>
              <EmptyTitle>该对象暂无可用生成动作</EmptyTitle>
              <EmptyDescription>
                阶段一仅开放服务端 allowedActions
                声明的专业媒体生成；文档候选请在对象详情中手动创建。
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </DialogContent>
    </Dialog>
  )
}
