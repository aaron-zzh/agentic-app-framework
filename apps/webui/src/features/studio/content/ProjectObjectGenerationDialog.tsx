/**
 * 项目对象媒体生成对话框：复用统一媒体工作台并严格走项目 Action。
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
import { useAigcProjectActions } from "@/lib/api/rest/ai/aigc"
import { OBJECT_TYPE_LABELS } from "./graph/graph-projection"

interface ProjectObjectGenerationDialogProps {
  open: boolean
  project: AigcProject
  object?: AigcProjectObject
  mutable: boolean
  onOpenChange: (open: boolean) => void
  onSubmitted?: (executionRunId: number) => void
}

function modeForAction(actionKey: string): Extract<MediaGenerationMode, "image" | "video"> | null {
  if (actionKey.startsWith("image.")) return "image"
  if (actionKey.startsWith("video.")) return "video"
  return null
}

/** 将服务端允许的媒体动作收敛为当前对象的显式项目目标。 */
export function buildProjectMediaTargets(
  actions: AigcActionOption[],
  projectId: number,
  object: AigcProjectObject
): Partial<Record<MediaGenerationMode, MediaProjectTarget>> {
  const targets: Partial<Record<MediaGenerationMode, MediaProjectTarget>> = {}
  for (const action of actions) {
    const mode = modeForAction(action.actionKey)
    if (!mode || !action.applicableObjectTypes.includes(object.objectType)) continue
    const existing = targets[mode]
    const preferredActionKey = `${mode}.generate`
    if (existing?.actionKey === preferredActionKey) continue
    if (existing && action.actionKey !== preferredActionKey) continue
    targets[mode] = { projectId, objectId: object.id, actionKey: action.actionKey }
  }
  return targets
}

/** 为当前项目对象展示服务端允许的图像或视频生成模式。 */
export function ProjectObjectGenerationDialog({
  open,
  project,
  object,
  mutable,
  onOpenChange,
  onSubmitted
}: ProjectObjectGenerationDialogProps) {
  const { data: actions = [], isLoading } = useAigcProjectActions(open && mutable ? project.id : null)
  const projectTargets = useMemo(
    () => (object ? buildProjectMediaTargets(actions, project.id, object) : {}),
    [actions, object, project.id]
  )
  const initialMode = projectTargets.image ? "image" : "video"
  const hasMode = projectTargets.image !== undefined || projectTargets.video !== undefined

  return (
    <Dialog open={open && mutable && object !== undefined} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            生成{object?.title || (object ? OBJECT_TYPE_LABELS[object.objectType] : "项目对象")}
          </DialogTitle>
          <DialogDescription>
            生成结果进入项目候选版本；项目模式不会提交独立创作任务。
          </DialogDescription>
        </DialogHeader>
        {isLoading ? (
          <Skeleton className="h-52 w-full" />
        ) : hasMode && object ? (
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
              <EmptyTitle>该对象暂无媒体生成动作</EmptyTitle>
              <EmptyDescription>仅显示项目配置中服务端允许的图片或视频动作。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </DialogContent>
    </Dialog>
  )
}
