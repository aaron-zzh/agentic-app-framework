/**
 * 生成面板——从底部弹起，包含参考素材区、Prompt 输入、参数栏
 * 支持 AI 生图 / AI 视频切换
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { AnimatePresence, m } from "framer-motion"
import { X } from "lucide-react"
import { useParams } from "next/navigation"
import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { ModelSelector } from "@/components/common/ModelSelector"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import type { VideoConfig } from "@/lib/api/rest/ai/ai-model"
import { request } from "@/lib/api/rest/entity/crud"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { useAigcStore } from "../store"
import { VOICE_TEXT_MAX_LEN, VOICES } from "../voice-options"
import { buildFinalPrompt, PromptInput } from "./PromptInput"
import { PromptTemplateDialog } from "./PromptTemplateDialog"
import { ReferenceDropZone } from "./ReferenceDropZone"
import { RoleSelector } from "./RoleSelector"

// ── 视频模式定义 ──────────────────────────────────────────────────
type VideoImageMode = "T2V" | "FIRST_FRAME" | "REFERENCE" | "EDIT"

const VIDEO_MODES: { mode: VideoImageMode; label: string; configKey: string }[] = [
  { mode: "T2V", label: "文生视频", configKey: "t2v" },
  { mode: "FIRST_FRAME", label: "图生视频", configKey: "i2v" },
  { mode: "REFERENCE", label: "多图参考", configKey: "r2v" },
  { mode: "EDIT", label: "视频编辑", configKey: "video-edit" }
]

/** 视频专属参数状态 */
interface VideoParams {
  resolution: string
  ratio: string
  duration: number | undefined
  seed: number | undefined
  promptExtend: boolean
  generateAudio: boolean
  audioSetting: string
  referenceVideoUrl: string
}

function initVideoParams(): VideoParams {
  return {
    resolution: "",
    ratio: "",
    duration: undefined,
    seed: undefined,
    promptExtend: false,
    generateAudio: false,
    audioSetting: "",
    referenceVideoUrl: ""
  }
}

/** 通用带标签 Select（视频参数栏内部复用） */
function VP({
  label,
  value,
  options,
  onChange
}: {
  label: string
  value: string
  options: (string | number)[]
  onChange: (v: string) => void
}) {
  const strOptions = options.map(String)
  return (
    <Select value={value} onValueChange={(v) => v && onChange(v)}>
      <SelectTrigger className="h-8 w-[120px] text-xs">
        <span className="shrink-0 text-muted-foreground">{label}</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {strOptions.map((o) => (
          <SelectItem key={o} value={o}>
            {o}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** 视频专属参数栏：根据 videoConfig 动态显示 */
function VideoParamsBar({
  config,
  params,
  onChange
}: {
  config: VideoConfig | undefined
  params: VideoParams
  onChange: (patch: Partial<VideoParams>) => void
}) {
  if (!config) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      {config.resolutions?.length && (
        <VP
          label="分辨率"
          value={params.resolution || config.resolutions[0]}
          options={config.resolutions}
          onChange={(v) => onChange({ resolution: v })}
        />
      )}
      {config.ratios?.length && (
        <VP
          label="比例"
          value={params.ratio || config.ratios[0]}
          options={config.ratios}
          onChange={(v) => onChange({ ratio: v })}
        />
      )}
      {config.durations?.length && (
        <VP
          label="时长"
          value={params.duration != null ? String(params.duration) : String(config.durations[0])}
          options={config.durations}
          onChange={(v) => onChange({ duration: Number(v) })}
        />
      )}
      {config.audioSetting?.length && (
        <VP
          label="音频"
          value={params.audioSetting || config.audioSetting[0]}
          options={config.audioSetting}
          onChange={(v) => onChange({ audioSetting: v })}
        />
      )}
      {config.seed && (
        <Input
          type="number"
          min={0}
          max={2147483647}
          value={params.seed && params.seed > 0 ? params.seed : ""}
          onChange={(e) => onChange({ seed: e.target.value ? Number(e.target.value) : undefined })}
          placeholder="Seed"
          className="h-8 w-[110px] text-xs"
        />
      )}
      {config.promptExtend && (
        <div className="flex items-center gap-1.5">
          <Switch
            id="video-prompt-extend"
            checked={params.promptExtend}
            onCheckedChange={(v) => onChange({ promptExtend: v })}
            className="h-4 w-7"
          />
          <Label
            htmlFor="video-prompt-extend"
            className="cursor-pointer text-muted-foreground text-xs"
          >
            智能改写
          </Label>
        </div>
      )}
      {config.generateAudio && (
        <div className="flex items-center gap-1.5">
          <Switch
            id="video-generate-audio"
            checked={params.generateAudio}
            onCheckedChange={(v) => onChange({ generateAudio: v })}
            className="h-4 w-7"
          />
          <Label
            htmlFor="video-generate-audio"
            className="cursor-pointer text-muted-foreground text-xs"
          >
            生成音频
          </Label>
        </div>
      )}
    </div>
  )
}

export function GenerationPanel() {
  const open = useAigcStore((s) => s.generationPanelOpen)
  const setOpen = useAigcStore((s) => s.setGenerationPanelOpen)
  const generationType = useAigcStore((s) => s.generationType)
  const setGenerationType = useAigcStore((s) => s.setGenerationType)
  const prompt = useAigcStore((s) => s.prompt)
  const setPrompt = useAigcStore((s) => s.setPrompt)
  const projectPromptTag = useAigcStore((s) => s.projectPromptTag)
  const projectPromptDismissed = useAigcStore((s) => s.projectPromptDismissed)
  const setProjectPromptDismissed = useAigcStore((s) => s.setProjectPromptDismissed)
  const isVideo = generationType === "VIDEO_GEN"
  const isAudioType = generationType === "VOICE" || generationType === "MUSIC"

  // ── 模型选择 + 生成参数 ──
  const { options, modelId, setModelId, currentModel } = useModelSelector(generationType)
  const { params, onChangeParams, resolvedSize } = useGenerationParams(currentModel)
  // ─────────────────────
  const agentRole = useAigcStore((s) => s.agentRole)
  const setAgentRole = useAigcStore((s) => s.setAgentRole)
  const negativePrompt = useAigcStore((s) => s.negativePrompt)
  const setNegativePrompt = useAigcStore((s) => s.setNegativePrompt)
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const clearReferenceAssets = useAigcStore((s) => s.clearReferenceAssets)

  // ── 视频模式状态 ──
  const videoConfig: VideoConfig | undefined = currentModel?.videoConfig
  const availableVideoModes = VIDEO_MODES.filter((m) => videoConfig?.modes?.includes(m.configKey))
  const [videoMode, setVideoMode] = useState<VideoImageMode>("T2V")
  const [videoParams, setVideoParams] = useState<VideoParams>(initVideoParams)
  const patchVideoParams = (patch: Partial<VideoParams>) =>
    setVideoParams((prev) => ({ ...prev, ...patch }))

  function handleVideoModeChange(mode: VideoImageMode) {
    setVideoMode(mode)
    clearReferenceAssets()
  }

  const isEditMode = !isVideo && !!currentModel?.imageConfig?.edit && referenceAssets.length > 0
  const promptMaxLength = currentModel?.contextWindow
    ? Math.min(currentModel.contextWindow, 3000)
    : 3000

  // 音频生成（配音/音乐）本地参数：音色、演唱声音
  const [voice, setVoice] = useState<string>(VOICES[0].value)
  const [musicGender, setMusicGender] = useState<"female" | "male">("female")

  const generateImage = useGenerateImage()
  const addPendingTask = useAigcStore((s) => s.addPendingTask)
  const routeParams = useParams()
  const projectId = routeParams.projectId
    ? Number(routeParams.projectId)
    : routeParams.id
      ? Number(routeParams.id)
      : null

  function handleGenerate() {
    if (!prompt.trim()) return
    const p = params
    const { width, height, sizePreset } = resolvedSize
    const cfg = currentModel?.imageConfig

    generateImage.mutate(
      {
        prompt: buildFinalPrompt(prompt, projectPromptDismissed ? null : projectPromptTag),
        displayPrompt: prompt || undefined,
        model: modelId,
        width,
        height,
        sizePreset: sizePreset,
        aspectRatio: cfg?.mode === "ratio" ? p.aspectRatio : undefined,
        imageUrls: isEditMode
          ? (referenceAssets
              .slice(0, currentModel?.imageConfig?.edit?.maxInputImages ?? 1)
              .map((a) => a.url)
              .filter(Boolean) as string[])
          : undefined,
        negativePrompt: currentModel?.imageConfig?.generate?.negativePrompt
          ? negativePrompt || undefined
          : undefined,
        seed: p.seed && p.seed > 0 ? p.seed : undefined,
        promptExtend: p.promptExtend,
        imageCount: p.imageCount,
        quality: p.quality,
        format: p.format,
        background: p.background,
        contentModeration: p.contentModeration,
        projectId: projectId ?? undefined
      },
      {
        onSuccess: (taskId) => {
          addPendingTask({
            id: taskId,
            prompt,
            type: isVideo ? "VIDEO_GEN" : "IMAGE_GEN",
            modelId: modelId
          })
          setPrompt("")
          setOpen(false)
        },
        onError: () => {}
      }
    )
  }

  function handleTypeChange(type: "IMAGE_GEN" | "VIDEO_GEN" | "VOICE" | "MUSIC") {
    setGenerationType(type)
  }

  /** 配音/音乐生成提交：复用统一任务接口 /aigc/tasks/submit */
  const audioGenerate = useMutation({
    mutationFn: (body: object) =>
      request<number>("/aigc/tasks/submit", { method: "POST", body: JSON.stringify(body) })
  })

  /** 视频生成提交 */
  const videoGenerate = useMutation({
    mutationFn: (body: object) =>
      request<number>("/aigc/tasks/submit", { method: "POST", body: JSON.stringify(body) })
  })

  function handleGenerateVideo() {
    if (!prompt.trim()) return
    const vp = videoParams
    const submitParams: Record<string, unknown> = {
      ...(videoMode !== "EDIT" && { imageMode: videoMode }),
      ...(vp.resolution && { resolution: vp.resolution }),
      ...(vp.ratio && { ratio: vp.ratio }),
      ...(vp.duration != null && { duration: vp.duration }),
      ...(vp.seed != null && vp.seed > 0 && { seed: vp.seed }),
      ...(videoConfig?.promptExtend && { promptExtend: vp.promptExtend }),
      ...(videoConfig?.generateAudio && { generateAudio: vp.generateAudio }),
      ...(vp.audioSetting && { audioSetting: vp.audioSetting })
    }
    if (videoMode === "FIRST_FRAME" && referenceAssets[0]?.url) {
      submitParams.imageUrl = referenceAssets[0].url
    } else if (videoMode === "REFERENCE" && referenceAssets.length > 0) {
      submitParams.referenceImageUrls = referenceAssets.map((a) => a.url).filter(Boolean)
    } else if (videoMode === "EDIT" && vp.referenceVideoUrl) {
      submitParams.referenceVideoUrls = [vp.referenceVideoUrl]
    }
    videoGenerate.mutate(
      {
        type: "VIDEO_GEN",
        prompt: prompt.trim(),
        projectId: projectId ?? null,
        model: modelId,
        params: submitParams
      },
      {
        onSuccess: (taskId) => {
          addPendingTask({ id: taskId, prompt, type: "VIDEO_GEN", modelId })
          setPrompt("")
          setOpen(false)
          toast.success("视频生成任务已提交")
        },
        onError: () => {}
      }
    )
  }

  /** 配音文本是否超出长度上限（仅配音类型校验） */
  const voiceOverLimit = generationType === "VOICE" && prompt.length > VOICE_TEXT_MAX_LEN

  function handleGenerateAudio() {
    const text = prompt.trim()
    if (!text || voiceOverLimit) return
    const isVoice = generationType === "VOICE"
    const type = isVoice ? "VOICE" : "MUSIC"
    audioGenerate.mutate(
      {
        type,
        prompt: text,
        projectId: projectId ?? null,
        params: isVoice ? { voice } : { gender: musicGender }
      },
      {
        onSuccess: (taskId) => {
          addPendingTask({ id: taskId, prompt: text, type })
          setPrompt("")
          setOpen(false)
          toast.success(isVoice ? "配音生成任务已提交" : "音乐生成任务已提交")
        },
        onError: () => {}
      }
    )
  }

  const [panelHeight, setPanelHeight] = useState<number>(() =>
    typeof window !== "undefined" ? Math.round(window.innerHeight * 0.6) : 400
  )
  const resizeStart = useRef<{ my: number; h: number } | null>(null)
  const handleResizeDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    resizeStart.current = {
      my: e.clientY,
      h: e.currentTarget.closest("[data-panel]")?.clientHeight ?? 400
    }
  }, [])
  const handleResizeMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizeStart.current) return
    setPanelHeight(Math.max(200, resizeStart.current.h + resizeStart.current.my - e.clientY))
  }, [])
  const handleResizeUp = useCallback(() => {
    resizeStart.current = null
  }, [])

  return (
    <AnimatePresence>
      {open && (
        <m.div
          data-panel
          initial={{ y: "100%" }}
          animate={{ y: 0 }}
          exit={{ y: "100%" }}
          transition={{ type: "spring", damping: 25, stiffness: 300 }}
          className="absolute inset-x-0 bottom-0 z-50 flex flex-col rounded-t-xl outline-hidden [background:linear-gradient(135deg,color-mix(in_oklch,var(--color-violet-500)_6%,transparent),transparent_50%,color-mix(in_oklch,var(--color-indigo-500)_6%,transparent)),var(--color-popover)] [box-shadow:0_-8px_32px_-4px_rgba(0,0,0,0.15),0_-2px_8px_-2px_rgba(0,0,0,0.1)]"
          style={{ height: panelHeight }}
        >
          {/* 顶部：拖拽手柄 + 收起按钮 */}
          <div className="relative flex items-center justify-center py-1.5">
            <div
              className="flex flex-1 cursor-ns-resize justify-center opacity-40 hover:opacity-80"
              onPointerDown={handleResizeDown}
              onPointerMove={handleResizeMove}
              onPointerUp={handleResizeUp}
            >
              <div className="h-1 w-10 rounded-full bg-muted-foreground" />
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              className="absolute top-0.5 right-1 opacity-60 hover:opacity-100"
              onClick={() => setOpen(false)}
              aria-label="关闭"
            >
              <X className="size-4" />
            </Button>
          </div>

          <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-4 pt-2">
            {/* 类型切换：生图 / 视频，图像编辑（模型支持时显示） */}
            <div className="flex items-center justify-between gap-2">
              <Tabs
                value={generationType}
                onValueChange={(v) =>
                  handleTypeChange(v as "IMAGE_GEN" | "VIDEO_GEN" | "VOICE" | "MUSIC")
                }
              >
                <TabsList className="h-7">
                  <TabsTrigger value="IMAGE_GEN" className="h-6 px-3 text-xs">
                    AI 生图
                  </TabsTrigger>
                  <TabsTrigger value="VIDEO_GEN" className="h-6 px-3 text-xs">
                    AI 视频
                  </TabsTrigger>
                  <TabsTrigger value="VOICE" className="h-6 px-3 text-xs">
                    配音生成
                  </TabsTrigger>
                  <TabsTrigger value="MUSIC" className="h-6 px-3 text-xs">
                    音乐生成
                  </TabsTrigger>
                </TabsList>
              </Tabs>
              <PromptTemplateDialog
                type={isVideo ? "VIDEO_GEN" : "IMAGE_GEN"}
                onSelect={(p) => setPrompt(p)}
                hasReferenceImages={!isVideo && referenceAssets.length > 0}
              />
            </div>

            {/* 参考素材拖入区 */}
            {!isAudioType && !isVideo && (
              <ReferenceDropZone
                max={
                  currentModel?.imageConfig?.edit
                    ? (currentModel.imageConfig.edit.maxInputImages ?? 1)
                    : 16
                }
                isEditMode={isEditMode}
              />
            )}

            {/* 视频：模式切换 + 对应输入区 */}
            {isVideo && availableVideoModes.length > 1 && (
              <div className="flex gap-1">
                {availableVideoModes.map(({ mode, label }) => (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => handleVideoModeChange(mode)}
                    className={`rounded-md px-3 py-1 text-xs transition-colors ${videoMode === mode ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-muted/80"}`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
            {isVideo && videoMode === "FIRST_FRAME" && <ReferenceDropZone max={1} />}
            {isVideo && videoMode === "REFERENCE" && (
              <ReferenceDropZone max={videoConfig?.maxReferenceImages ?? 4} />
            )}
            {isVideo && videoMode === "EDIT" && (
              <Input
                value={videoParams.referenceVideoUrl}
                onChange={(e) => patchVideoParams({ referenceVideoUrl: e.target.value })}
                placeholder="输入参考视频 URL..."
                className="h-8 text-xs"
              />
            )}
            <div className="flex min-h-0 flex-1 flex-col gap-1">
              {referenceAssets.length > 0 && isEditMode && (
                <span className="flex w-fit items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 font-medium text-amber-600/80 text-xs">
                  <svg className="size-3" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                    <path
                      d="M11 2L14 5L5 14H2V11L11 2Z"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinejoin="round"
                    />
                  </svg>
                  图像编辑
                </span>
              )}
              <PromptInput
                value={prompt}
                onChange={setPrompt}
                placeholder={
                  isVideo
                    ? "描述你想生成的视频内容..."
                    : generationType === "VOICE"
                      ? "输入需要配音的文本内容（最多 200 字）..."
                      : generationType === "MUSIC"
                        ? "描述音乐风格、主题、情绪，或直接填入歌词..."
                        : "描述你想生成的图像..."
                }
                projectPrompt={projectPromptTag}
                dismissed={projectPromptDismissed}
                onDismissedChange={setProjectPromptDismissed}
                maxLength={promptMaxLength}
                className="flex-1"
                minHeight={40}
              />
            </div>

            {/* 反向提示词：modeConfig 支持时显示 */}
            {!isVideo &&
              (isEditMode
                ? currentModel?.imageConfig?.edit?.negativePrompt
                : currentModel?.imageConfig?.generate?.negativePrompt) && (
                <Textarea
                  value={negativePrompt}
                  onChange={(e) => setNegativePrompt(e.target.value)}
                  placeholder="反向提示词（不希望出现的内容）"
                  className="max-h-[80px] resize-none text-muted-foreground text-xs"
                  rows={2}
                />
              )}
          </div>

          {/* 底部参数栏 */}
          {!isAudioType && (
            <div className="shrink-0 border-t px-4 py-3">
              <div className="flex flex-wrap items-center justify-center gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  {/* 模型选择 */}
                  <ModelSelector
                    variant="select"
                    options={options}
                    value={modelId}
                    onChange={(id) => setModelId(id)}
                    className="h-8 w-[180px] text-xs"
                  />
                  {/* 视频专属参数 */}
                  {isVideo && (
                    <VideoParamsBar
                      config={videoConfig}
                      params={videoParams}
                      onChange={patchVideoParams}
                    />
                  )}
                  {/* 图像参数控件——根据模型配置动态渲染 */}
                  {!isVideo && (
                    <ModelParamsBar
                      model={currentModel}
                      params={params}
                      onChangeParams={onChangeParams}
                      isEditMode={isEditMode}
                    />
                  )}
                </div>

                <RoleSelector value={agentRole} onChange={setAgentRole} />

                <Button
                  size="sm"
                  disabled={
                    isVideo
                      ? videoGenerate.isPending || !prompt.trim()
                      : generateImage.isPending || !prompt.trim() || prompt.length > promptMaxLength
                  }
                  onClick={isVideo ? handleGenerateVideo : handleGenerate}
                  className="bg-linear-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
                >
                  {(isVideo ? videoGenerate.isPending : generateImage.isPending)
                    ? "生成中..."
                    : "生成"}
                </Button>
              </div>
            </div>
          )}

          {/* 音频参数栏（配音/音乐）：音色或演唱声音 + 生成按钮 */}
          {isAudioType && (
            <div className="shrink-0 border-t px-4 py-3">
              <div className="flex flex-wrap items-center justify-center gap-2">
                {generationType === "VOICE" ? (
                  <Select value={voice} onValueChange={(v) => v && setVoice(v)}>
                    <SelectTrigger className="h-8 w-44 text-xs">
                      <span className="shrink-0 text-muted-foreground">音色</span>
                      <span className="truncate">
                        {VOICES.find((x) => x.value === voice)?.label ?? voice}
                      </span>
                    </SelectTrigger>
                    <SelectContent>
                      {VOICES.map((v) => (
                        <SelectItem key={v.value} value={v.value}>
                          {v.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <Select
                    value={musicGender}
                    onValueChange={(v) => v && setMusicGender(v as "female" | "male")}
                  >
                    <SelectTrigger className="h-8 w-28 text-xs">
                      <span className="shrink-0 text-muted-foreground">声音</span>
                      <span>{musicGender === "female" ? "女声" : "男声"}</span>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="female">女声</SelectItem>
                      <SelectItem value="male">男声</SelectItem>
                    </SelectContent>
                  </Select>
                )}

                {generationType === "VOICE" && (
                  <span
                    className={
                      voiceOverLimit ? "text-destructive text-xs" : "text-muted-foreground text-xs"
                    }
                  >
                    {prompt.length}/{VOICE_TEXT_MAX_LEN}
                  </span>
                )}

                <Button
                  size="sm"
                  disabled={audioGenerate.isPending || !prompt.trim() || voiceOverLimit}
                  onClick={handleGenerateAudio}
                  className="bg-linear-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
                >
                  {audioGenerate.isPending ? "生成中..." : "生成"}
                </Button>
              </div>
            </div>
          )}
        </m.div>
      )}
    </AnimatePresence>
  )
}
