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
import { useCallback, useEffect, useState } from "react"
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
  MediaTaskSubmission,
  MediaTaskType
} from "@/features/studio/media-generation/types"
import {
  isMediaTaskType,
  MODE_BY_TASK_TYPE,
  RESULT_TYPE_BY_MODE,
  TASK_TYPE_BY_MODE
} from "@/features/studio/media-generation/types"
import type { VideoImageMode } from "@/lib/api/rest/ai"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useSlotStore } from "@/features/studio/slots/store"
import { cn } from "@/lib/utils"

interface Feature {
  key: MediaGenerationMode
  label: string
  href: string
  activeClass: string
}

const FEATURES: Feature[] = [
  {
    key: "image",
    label: "AI 生图",
    href: "/studio/create/image",
    activeClass: "border-violet-500/40 bg-violet-500/15 text-violet-400"
  },
  {
    key: "video",
    label: "AI 视频",
    href: "/studio/create/video",
    activeClass: "border-cyan-500/40 bg-cyan-500/15 text-cyan-400"
  },
  {
    key: "voice",
    label: "配音",
    href: "/studio/create/voice",
    activeClass: "border-rose-500/40 bg-rose-500/15 text-rose-400"
  },
  {
    key: "music",
    label: "音乐",
    href: "/studio/create/music",
    activeClass: "border-emerald-500/40 bg-emerald-500/15 text-emerald-400"
  },
  {
    key: "model-3d",
    label: "3D",
    href: "/studio/create/tools/3d",
    activeClass: "border-violet-500/40 bg-violet-500/15 text-violet-400"
  }
]

const FEATURE_BY_MODE = Object.fromEntries(
  FEATURES.map((feature) => [feature.key, feature])
) as Record<MediaGenerationMode, Feature>

interface ParsedTaskParams {
  imageUrls?: string[]
  imageUrl?: string
  referenceImageUrls?: string[]
  imageMode?: VideoImageMode
}

interface WorkspaceDraft {
  mode: MediaGenerationMode
  value: MediaGenerationDraft
}

interface MediaGenerationWorkspaceProps {
  initialMode?: MediaGenerationMode
  projectId?: number | null
}

function parseTaskParams(params: string | null): ParsedTaskParams {
  if (!params) return {}
  try {
    return JSON.parse(params) as ParsedTaskParams
  } catch {
    return {}
  }
}

function resolveVideoImageMode(params: ParsedTaskParams): VideoImageMode {
  if (
    params.imageMode === "T2V" ||
    params.imageMode === "FIRST_FRAME" ||
    params.imageMode === "REFERENCE"
  ) {
    return params.imageMode
  }
  if ((params.referenceImageUrls?.length ?? 0) > 1) return "REFERENCE"
  if (params.imageUrl || params.referenceImageUrls?.length) return "FIRST_FRAME"
  return "T2V"
}

/** 组合五种媒体 Composer，并呈现当前模式的实时任务结果。 */
export function MediaGenerationWorkspace({
  initialMode = "image",
  projectId = null
}: MediaGenerationWorkspaceProps) {
  const [activeMode, setActiveMode] = useState<MediaGenerationMode>(initialMode)
  const [workspaceDraft, setWorkspaceDraft] = useState<WorkspaceDraft | null>(null)
  const [tasksByType, setTasksByType] = useState<Record<MediaTaskType, AigcTaskEvent[]>>({
    IMAGE: [],
    VIDEO: [],
    VOICE: [],
    MUSIC: [],
    MODEL_3D: []
  })

  const router = useRouter()
  const openSlot = useSlotStore((state) => state.openSlot)
  const setSelectedSkillId = useAigcStore((state) => state.setSelectedSkillId)

  useEffect(() => {
    setActiveMode(initialMode)
  }, [initialMode])

  const upsertCreatedTask = useCallback((task: AigcTaskEvent) => {
    if (!isMediaTaskType(task.type)) return
    setTasksByType((current) => ({
      ...current,
      [task.type]: [task, ...current[task.type].filter((item) => item.id !== task.id)].slice(0, 5)
    }))
  }, [])

  const updateTask = useCallback((task: AigcTaskEvent) => {
    if (!isMediaTaskType(task.type)) return
    setTasksByType((current) => ({
      ...current,
      [task.type]: current[task.type].map((item) => (item.id === task.id ? task : item))
    }))
  }, [])

  useAigcTaskStream({
    onCreated: upsertCreatedTask,
    onCompleted: updateTask,
    onFailed: updateTask
  })

  const handleModeChange = useCallback(
    (mode: MediaGenerationMode) => {
      const feature = FEATURE_BY_MODE[mode]
      setActiveMode(mode)
      setWorkspaceDraft(null)
      setSelectedSkillId(null)
      router.push(feature.href)
    },
    [router, setSelectedSkillId]
  )

  const handleTaskSubmitted = useCallback(
    (_submission: MediaTaskSubmission) => {
      openSlot({ panelType: "recent-tasks" })
    },
    [openSlot]
  )

  const handleRegenerate = useCallback(
    (task: AigcTaskEvent) => {
      if (!isMediaTaskType(task.type)) return
      const mode = MODE_BY_TASK_TYPE[task.type]
      const params = parseTaskParams(task.params)
      const referenceImageUrl =
        params.imageUrls?.[0] ?? params.imageUrl ?? params.referenceImageUrls?.[0]
      const lastFrameImageUrl = params.referenceImageUrls?.[1]

      setActiveMode(mode)
      setWorkspaceDraft((current) => ({
        mode,
        value: {
          revision: (current?.value.revision ?? 0) + 1,
          prompt: task.prompt ?? "",
          model: task.model ?? undefined,
          referenceImageUrl,
          lastFrameImageUrl,
          videoImageMode: mode === "video" ? resolveVideoImageMode(params) : undefined
        }
      }))
      router.push(FEATURE_BY_MODE[mode].href)
    },
    [router]
  )

  const activeFeature = FEATURE_BY_MODE[activeMode]
  const activeTaskType = TASK_TYPE_BY_MODE[activeMode]
  const activeDraft = workspaceDraft?.mode === activeMode ? workspaceDraft.value : undefined
  const composerKey = `${activeMode}-${activeDraft?.revision ?? 0}`
  const modeSelector = (
    <select
      value={activeMode}
      onChange={(event) => handleModeChange(event.target.value as MediaGenerationMode)}
      aria-label="生成模式"
      className={cn(
        "h-8 shrink-0 rounded-lg border px-2.5 text-xs outline-none",
        activeFeature.activeClass
      )}
    >
      {FEATURES.map((feature) => (
        <option key={feature.key} value={feature.key}>
          {feature.label}
        </option>
      ))}
    </select>
  )

  const composerProps: MediaGenerationComposerProps = {
    projectId,
    initialDraft: activeDraft,
    onTaskSubmitted: handleTaskSubmitted,
    leadingTools: modeSelector
  }

  let composer: ReactNode
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

  return (
    <>
      {composer}
      <GenerationResultCard
        tasks={tasksByType[activeTaskType]}
        mediaType={RESULT_TYPE_BY_MODE[activeMode]}
        onRegenerate={handleRegenerate}
      />
    </>
  )
}