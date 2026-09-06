/**
 * Studio 媒体生成统一工作台。
 *
 * 页面层只负责模式路由、任务流和结果展示；各模式的输入、参数与提交逻辑由
 * 可独立嵌入页面或画布节点的 controller/composer 承担。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import type { ReactNode } from "react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { useAigcStore } from "@/features/aigc/store"
import { ImageGenerationComposer } from "@/features/studio/media-generation/components/ImageGenerationComposer"
import { Model3dGenerationComposer } from "@/features/studio/media-generation/components/Model3dGenerationComposer"
import { MusicGenerationComposer } from "@/features/studio/media-generation/components/MusicGenerationComposer"
import { VideoGenerationComposer } from "@/features/studio/media-generation/components/VideoGenerationComposer"
import { VoiceGenerationComposer } from "@/features/studio/media-generation/components/VoiceGenerationComposer"
import type {
  MediaGenerationComposerProps,
  MediaGenerationDraft,
  MediaGenerationMode,
  MediaProjectTarget,
  MediaTaskSubmission,
  MediaTaskType
} from "@/features/studio/media-generation/types"
import {
  getMediaGenerationPath,
  isMediaTaskType,
  RESULT_TYPE_BY_MODE,
  TASK_TYPE_BY_MODE
} from "@/features/studio/media-generation/types"
import { useSlotStore } from "@/features/studio/slots/store"
import { type AigcTaskEvent } from "@/lib/api/rest/ai/aigc-task"
import { request } from "@/lib/api/rest/entity"
import type { PageResult } from "@/lib/api/types"

interface Feature {
  key: MediaGenerationMode
  label: string
}

const FEATURES: Feature[] = [
  { key: "image", label: "AI 生图" },
  { key: "video", label: "AI 视频" },
  { key: "voice", label: "配音" },
  { key: "music", label: "音乐" },
  { key: "model-3d", label: "3D" }
]

type ParsedTaskParams = Record<string, unknown>

interface WorkspaceDraft {
  mode: MediaGenerationMode
  value: MediaGenerationDraft
}

interface MediaGenerationWorkspaceProps {
  initialMode?: MediaGenerationMode
  initialDraft?: MediaGenerationDraft
  projectTargets?: Partial<Record<MediaGenerationMode, MediaProjectTarget>>
  onExecutionSubmitted?: (executionRunId: number) => void
}

function parseTaskParams(params: string | null): ParsedTaskParams {
  if (!params) return {}
  try {
    const parsed: unknown = JSON.parse(params)
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as ParsedTaskParams)
      : {}
  } catch {
    return {}
  }
}

/** 项目 Action 模式不读取也不复制独立 AIGC Task 服务端状态。 */
export function shouldLoadIndependentMediaTasks(
  projectTargets: MediaGenerationWorkspaceProps["projectTargets"]
): boolean {
  return projectTargets === undefined
}

/** 组合五种媒体 Composer，并呈现当前模式的实时任务结果。 */
export function MediaGenerationWorkspace({
  initialMode = "image",
  initialDraft,
  projectTargets,
  onExecutionSubmitted
}: MediaGenerationWorkspaceProps) {
  const availableFeatures = useMemo(
    () =>
      projectTargets
        ? FEATURES.filter((feature) => projectTargets[feature.key] !== undefined)
        : FEATURES,
    [projectTargets]
  )
  const resolvedInitialMode =
    availableFeatures.find((feature) => feature.key === initialMode)?.key ??
    availableFeatures[0]?.key ??
    initialMode
  const [activeMode, setActiveMode] = useState<MediaGenerationMode>(resolvedInitialMode)
  const [workspaceDraft, setWorkspaceDraft] = useState<WorkspaceDraft | null>(() =>
    initialDraft ? { mode: resolvedInitialMode, value: initialDraft } : null
  )
  const [tasksByType, setTasksByType] = useState<Record<MediaTaskType, AigcTaskEvent[]>>({
    IMAGE: [],
    VIDEO: [],
    VOICE: [],
    MUSIC: [],
    MODEL_3D: []
  })
  const [regeneratingTaskId, setRegeneratingTaskId] = useState<number | null>(null)

  const router = useRouter()
  const openSlot = useSlotStore((state) => state.openSlot)
  const setSelectedSkillId = useAigcStore((state) => state.setSelectedSkillId)

  useEffect(() => {
    setActiveMode(resolvedInitialMode)
    setWorkspaceDraft(initialDraft ? { mode: resolvedInitialMode, value: initialDraft } : null)
  }, [initialDraft, resolvedInitialMode])

  useEffect(() => {
    if (!shouldLoadIndependentMediaTasks(projectTargets)) return
    let active = true
    const loadTasks = async () => {
      try {
        const page = await request<PageResult<AigcTaskEvent>>(
          "/aigc/tasks?pageNo=1&pageSize=25&sort=id:desc"
        )
        if (!active) return
        const next: Record<MediaTaskType, AigcTaskEvent[]> = {
          IMAGE: [],
          VIDEO: [],
          VOICE: [],
          MUSIC: [],
          MODEL_3D: []
        }
        for (const task of page.list) {
          if (isMediaTaskType(task.type) && next[task.type].length < 5) {
            next[task.type].push(task)
          }
        }
        setTasksByType(next)
      } catch {
        // 下一轮刷新会重试
      }
    }
    loadTasks()
    const timer = window.setInterval(loadTasks, 3_000)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [projectTargets])

  const handleModeChange = useCallback(
    (mode: MediaGenerationMode) => {
      setActiveMode(mode)
      setWorkspaceDraft(null)
      setSelectedSkillId(null)
      if (!projectTargets) router.push(getMediaGenerationPath(mode))
    },
    [projectTargets, router, setSelectedSkillId]
  )

  const handleTaskSubmitted = useCallback(
    (submission: MediaTaskSubmission) => {
      if (submission.executionRunId !== undefined) {
        onExecutionSubmitted?.(submission.executionRunId)
        return
      }
      openSlot({ panelType: "recent-tasks" })
    },
    [onExecutionSubmitted, openSlot]
  )

  const handleRegenerate = useCallback(
    async (task: AigcTaskEvent) => {
      if (!isMediaTaskType(task.type)) return
      const prompt = task.prompt?.trim()
      if (!prompt) {
        toast.error("原任务缺少生成提示词")
        return
      }

      setRegeneratingTaskId(task.id)
      try {
        const taskId = await request<number>("/aigc/tasks/submit", {
          method: "POST",
          body: JSON.stringify({
            type: task.type,
            prompt,
            displayPrompt: prompt,
            model: task.model,
            params: parseTaskParams(task.params)
          })
        })
        toast.success(`重新生成任务已提交（#${taskId}）`)
        openSlot({ panelType: "recent-tasks" })
      } catch {
        // API 客户端已统一提示请求错误
      } finally {
        setRegeneratingTaskId(null)
      }
    },
    [openSlot]
  )

  const activeTaskType = TASK_TYPE_BY_MODE[activeMode]
  const activeDraft = workspaceDraft?.mode === activeMode ? workspaceDraft.value : undefined
  const activeProjectTarget = projectTargets?.[activeMode]
  const composerKey = `${activeMode}-${JSON.stringify(activeDraft ?? null)}`
  const modeSelector = (
    <Tabs
      value={activeMode}
      onValueChange={(value) => handleModeChange(value as MediaGenerationMode)}
      className="min-w-0"
    >
      <TabsList className="h-8 max-w-full justify-start overflow-x-auto">
        {availableFeatures.map((feature) => (
          <TabsTrigger key={feature.key} value={feature.key} className="h-7 px-3 text-xs">
            {feature.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )

  const composerProps: MediaGenerationComposerProps = {
    projectTarget: activeProjectTarget,
    initialDraft: activeDraft,
    onTaskSubmitted: handleTaskSubmitted,
    leadingTools: modeSelector
  }

  let composer: ReactNode = null
  if (!projectTargets || activeProjectTarget) {
    switch (activeMode) {
      case "image":
        composer = <ImageGenerationComposer key={composerKey} {...composerProps} />
        break
      case "video":
        composer = <VideoGenerationComposer key={composerKey} {...composerProps} />
        break
      case "voice":
        composer = <VoiceGenerationComposer key={composerKey} {...composerProps} />
        break
      case "music":
        composer = <MusicGenerationComposer key={composerKey} {...composerProps} />
        break
      case "model-3d":
        composer = <Model3dGenerationComposer key={composerKey} {...composerProps} />
        break
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {composer}
      {projectTargets ? null : (
        <GenerationResultCard
          tasks={tasksByType[activeTaskType]}
          mediaType={RESULT_TYPE_BY_MODE[activeMode]}
          onRegenerate={handleRegenerate}
          regeneratingTaskId={regeneratingTaskId}
        />
      )}
    </div>
  )
}
