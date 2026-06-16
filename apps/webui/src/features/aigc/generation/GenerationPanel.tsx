/**
 * 生成面板——从底部弹起，包含参考素材区、Prompt 输入、参数栏
 * 支持 AI 生图 / AI 视频切换
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation, useQuery } from "@tanstack/react-query"
import { AnimatePresence, m } from "framer-motion"
import { X } from "lucide-react"
import { useParams } from "next/navigation"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
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
import type { ImageConfig } from "@/lib/api/rest/ai/ai-model"
import { calcRatio, listImageModels, listVideoModels } from "@/lib/api/rest/ai/ai-model"
import { request } from "@/lib/api/rest/entity/crud"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { useAigcStore } from "../store"
import { VOICE_TEXT_MAX_LEN, VOICES } from "../voice-options"
import { buildFinalPrompt, PromptInput } from "./PromptInput"
import { PromptTemplateDialog } from "./PromptTemplateDialog"
import { ReferenceDropZone } from "./ReferenceDropZone"
import { RoleSelector } from "./RoleSelector"

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
  const model = useAigcStore((s) => s.model)
  const setModel = useAigcStore((s) => s.setModel)
  const resolution = useAigcStore((s) => s.resolution)
  const setResolution = useAigcStore((s) => s.setResolution)
  const aspectRatio = useAigcStore((s) => s.aspectRatio)
  const setAspectRatio = useAigcStore((s) => s.setAspectRatio)
  const videoDuration = useAigcStore((s) => s.videoDuration)
  const setVideoDuration = useAigcStore((s) => s.setVideoDuration)
  const agentRole = useAigcStore((s) => s.agentRole)
  const setAgentRole = useAigcStore((s) => s.setAgentRole)
  const seed = useAigcStore((s) => s.seed)
  const setSeed = useAigcStore((s) => s.setSeed)
  const promptExtend = useAigcStore((s) => s.promptExtend)
  const setPromptExtend = useAigcStore((s) => s.setPromptExtend)
  const negativePrompt = useAigcStore((s) => s.negativePrompt)
  const setNegativePrompt = useAigcStore((s) => s.setNegativePrompt)
  const imageCount = useAigcStore((s) => s.imageCount)
  const setImageCount = useAigcStore((s) => s.setImageCount)
  const quality = useAigcStore((s) => s.quality)
  const setQuality = useAigcStore((s) => s.setQuality)
  const format = useAigcStore((s) => s.format)
  const setFormat = useAigcStore((s) => s.setFormat)
  const sizePreset = useAigcStore((s) => s.sizePreset)
  const setSizePreset = useAigcStore((s) => s.setSizePreset)
  const background = useAigcStore((s) => s.background)
  const setBackground = useAigcStore((s) => s.setBackground)
  const contentModeration = useAigcStore((s) => s.contentModeration)
  const setContentModeration = useAigcStore((s) => s.setContentModeration)
  const referenceAssets = useAigcStore((s) => s.referenceAssets)

  const { data: imageModels = [] } = useQuery({
    queryKey: ["ai", "models", "image"],
    queryFn: listImageModels,
    staleTime: 5 * 60 * 1000
  })
  const { data: videoModels = [] } = useQuery({
    queryKey: ["ai", "models", "video"],
    queryFn: listVideoModels,
    staleTime: 5 * 60 * 1000
  })

  useEffect(() => {
    if (!model && imageModels.length > 0) setModel(imageModels[0].modelId)
  }, [model, imageModels, setModel])

  const isVideo = generationType === "video"
  const isAudioType = generationType === "voice" || generationType === "music"
  const currentModelMeta = (isVideo ? videoModels : imageModels).find((m) => m.modelId === model)
  const imageConfig: ImageConfig | undefined = currentModelMeta?.imageConfig
  // 当前是否为图像编辑模式（有参考图且模型支持编辑）
  const isEditMode = !isVideo && !!imageConfig?.edit && referenceAssets.length > 0
  // 当前模式的参数配置
  const modeConfig = isEditMode ? imageConfig?.edit : imageConfig?.generate
  // prompt 最大字符数：优先用模型配置，其次按 contextWindow 直接作为字符数上限，兜底 3000
  const promptMaxLength = currentModelMeta?.contextWindow
    ? Math.min(currentModelMeta.contextWindow, 3000)
    : 3000

  // 尺寸选择值，格式 "WxH"
  const [fixedSize, setFixedSize] = useState<string>("")

  // 音频生成（配音/音乐）本地参数：音色、演唱声音
  const [voice, setVoice] = useState<string>(VOICES[0].value)
  const [musicGender, setMusicGender] = useState<"female" | "male">("female")

  // 切换模型时重置 fixedSize 为默认中间项
  useEffect(() => {
    if (!imageConfig?.sizes) return
    if (imageConfig.mode === "fixed") {
      const fixed = imageConfig.sizes as [number, number][]
      if (fixed.length > 0) {
        const [w, h] = fixed[Math.floor(fixed.length / 2)]
        setFixedSize(`${w}x${h}`)
      }
    } else if (imageConfig.mode === "ratio") {
      const ratio = imageConfig.sizes as Record<string, [number, number][]>
      const firstRatio = Object.keys(ratio)[0]
      const firstSize = firstRatio ? ratio[firstRatio]?.[0] : undefined
      if (firstSize) setFixedSize(`${firstSize[0]}x${firstSize[1]}`)
    }
  }, [imageConfig?.sizes, imageConfig?.mode])

  // 切换模型时重置 quality 为该模型支持的第一个值（避免传不支持的值）
  useEffect(() => {
    const supported = imageConfig?.generate?.quality ?? imageConfig?.edit?.quality
    if (supported && supported.length > 0 && !supported.includes(quality)) {
      setQuality(supported.includes("auto") ? "auto" : supported[0])
    }
  }, [imageConfig, quality, setQuality])

  const generateImage = useGenerateImage()
  const addPendingTask = useAigcStore((s) => s.addPendingTask)
  const routeParams = useParams()
  const projectId = routeParams.projectId ? Number(routeParams.projectId) : null

  /** ratio 模式无精确尺寸时的降级换算 */
  function resolveSize(res: string, ratio: string): { width: number; height: number } {
    const base = res === "1K" ? 1024 : res === "2K" ? 2048 : res === "4K" ? 4096 : 1024
    const map: Record<string, [number, number]> = {
      "1:1": [1, 1],
      "16:9": [16, 9],
      "9:16": [9, 16],
      "4:3": [4, 3],
      "3:4": [3, 4]
    }
    const [rw, rh] = map[ratio] ?? [1, 1]
    if (rw >= rh) return { width: base, height: Math.round((base * rh) / rw / 64) * 64 }
    return { width: Math.round((base * rw) / rh / 64) * 64, height: base }
  }

  function handleGenerate() {
    if (!prompt.trim()) return
    let width: number, height: number, sizeStr: string | undefined
    if (modeConfig?.sizePresets?.length) {
      // 档位模式：直接传档位字符串，不传像素
      sizeStr = sizePreset
      width = 1024
      height = 1024 // 占位，后端忽略
    } else if (fixedSize) {
      const [w, h] = fixedSize.split("x").map(Number)
      width = w || 1024
      height = h || 1024
    } else {
      ;({ width, height } = resolveSize(resolution, aspectRatio))
    }
    generateImage.mutate(
      {
        prompt: buildFinalPrompt(prompt, projectPromptDismissed ? null : projectPromptTag),
        displayPrompt: prompt || undefined,
        model,
        width,
        height,
        sizePreset: sizeStr,
        // ratio 模式传 aspectRatio 供 Gemini 等模型使用
        aspectRatio: imageConfig?.mode === "ratio" ? aspectRatio : undefined,
        // features.edit=true 且有参考图时自动走图像编辑，最多取 maxEditImages 张
        imageUrls: isEditMode
          ? (referenceAssets
              .slice(0, modeConfig?.maxInputImages ?? 1)
              .map((a) => a.url)
              .filter(Boolean) as string[])
          : undefined,
        negativePrompt: modeConfig?.negativePrompt ? negativePrompt || undefined : undefined,
        seed: modeConfig?.seed && seed > 0 ? seed : undefined,
        promptExtend: modeConfig?.promptExtend ? promptExtend : undefined,
        imageCount: (modeConfig?.maxImages ?? 1) > 1 ? imageCount : undefined,
        quality: modeConfig?.quality ? quality : undefined,
        format: modeConfig?.format ? format : undefined,
        background: modeConfig?.background ? background : undefined,
        contentModeration: modeConfig?.contentModeration ? contentModeration : undefined,
        projectId: projectId ?? undefined
      },
      {
        onSuccess: (taskId) => {
          addPendingTask({ id: taskId, prompt, type: isVideo ? "VIDEO" : "IMAGE", modelId: model })
          setPrompt("")
          setOpen(false)
        },
        onError: () => {}
      }
    )
  }

  function handleTypeChange(type: "image" | "video" | "voice" | "music") {
    setGenerationType(type)
    setModel(type === "image" ? (imageModels[0]?.modelId ?? "") : (videoModels[0]?.modelId ?? ""))
  }

  /** 配音/音乐生成提交：复用统一任务接口 /aigc/tasks/submit */
  const audioGenerate = useMutation({
    mutationFn: (body: object) =>
      request<number>("/aigc/tasks/submit", { method: "POST", body: JSON.stringify(body) })
  })

  /** 配音文本是否超出长度上限（仅配音类型校验） */
  const voiceOverLimit = generationType === "voice" && prompt.length > VOICE_TEXT_MAX_LEN

  function handleGenerateAudio() {
    const text = prompt.trim()
    if (!text || voiceOverLimit) return
    const isVoice = generationType === "voice"
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
        onError: (err) => toast.error((err as Error).message ?? "提交失败")
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

  /** 渲染图像尺寸控件 */
  function renderSizeControl() {
    // sizePresets 优先：档位选择（1K/2K/4K）
    if (modeConfig?.sizePresets && modeConfig.sizePresets.length > 0) {
      const presetSelect = (
        <Select value={sizePreset} onValueChange={(v) => v != null && setSizePreset(v)}>
          <SelectTrigger className="h-8 w-[90px] text-xs">
            <span className="shrink-0 text-muted-foreground">规格</span>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {modeConfig.sizePresets.map((p) => (
              <SelectItem key={p} value={p}>
                {p}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )
      // ratio 模式：同时显示比例选择
      if (imageConfig?.mode === "ratio") {
        const ratios = Object.keys((imageConfig.sizes ?? {}) as Record<string, unknown>)
        return (
          <>
            <Select value={aspectRatio} onValueChange={(v) => setAspectRatio(v ?? ratios[0])}>
              <SelectTrigger className="h-8 w-[100px] text-xs">
                <span className="shrink-0 text-muted-foreground">比例</span>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ratios.map((v) => (
                  <SelectItem key={v} value={v}>
                    {v}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {presetSelect}
          </>
        )
      }
      return presetSelect
    }

    if (!imageConfig) {
      // 无配置：降级档位+比例
      return (
        <>
          <Select value={aspectRatio} onValueChange={(v) => setAspectRatio(v ?? "1:1")}>
            <SelectTrigger className="h-8 w-[100px] text-xs">
              <span className="shrink-0 text-muted-foreground">比例</span>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {["1:1", "9:16", "16:9", "4:3", "3:4"].map((v) => (
                <SelectItem key={v} value={v}>
                  {v}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={resolution} onValueChange={(v) => setResolution(v ?? "2K")}>
            <SelectTrigger className="h-8 w-[90px] text-xs">
              <span className="shrink-0 text-muted-foreground">分辨率</span>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {["1K", "2K", "4K"].map((r) => (
                <SelectItem key={r} value={r}>
                  {r}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </>
      )
    }

    if (imageConfig.mode === "fixed") {
      const fixedSizes = imageConfig.sizes as [number, number][]
      return (
        <Select value={fixedSize} onValueChange={(v) => v != null && setFixedSize(v)}>
          <SelectTrigger className="h-8 w-[150px] text-xs">
            <span className="shrink-0 text-muted-foreground">尺寸</span>
            {fixedSize
              ? (() => {
                  const [w, h] = fixedSize.split("x").map(Number)
                  return (
                    <span className="truncate">
                      {calcRatio(w, h)} {w}×{h}
                    </span>
                  )
                })()
              : null}
          </SelectTrigger>
          <SelectContent>
            {fixedSizes.map(([w, h]) => {
              const ratio = calcRatio(w, h)
              const ms = 14
              const rw = w >= h ? ms : Math.round((ms * w) / h)
              const rh = h >= w ? ms : Math.round((ms * h) / w)
              return (
                <SelectItem key={`${w}x${h}`} value={`${w}x${h}`}>
                  <span className="flex items-center gap-1.5">
                    <svg
                      width="16"
                      height="16"
                      viewBox="0 0 16 16"
                      aria-hidden="true"
                      className="shrink-0 text-muted-foreground"
                    >
                      <rect
                        x={(16 - rw) / 2 + 0.5}
                        y={(16 - rh) / 2 + 0.5}
                        width={rw - 1}
                        height={rh - 1}
                        rx="1"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.5"
                      />
                    </svg>
                    <span className="text-muted-foreground">{ratio}</span>
                    <span>
                      {w}×{h}
                    </span>
                  </span>
                </SelectItem>
              )
            })}
          </SelectContent>
        </Select>
      )
    }

    // ratio 模式
    const ratioSizes = (imageConfig.sizes ?? {}) as Record<string, [number, number][]>
    const ratios = Object.keys(ratioSizes)
    const sizesForRatio: [number, number][] = ratioSizes[aspectRatio] ?? []
    const currentRatioSize =
      sizesForRatio.length > 0
        ? sizesForRatio.some(([w, h]) => `${w}x${h}` === fixedSize)
          ? fixedSize
          : `${sizesForRatio[0][0]}x${sizesForRatio[0][1]}`
        : fixedSize
    return (
      <>
        <Select
          value={aspectRatio}
          onValueChange={(v) => {
            const val = v ?? ratios[0]
            setAspectRatio(val)
            const first = ratioSizes[val]?.[0]
            if (first) setFixedSize(`${first[0]}x${first[1]}`)
          }}
        >
          <SelectTrigger className="h-8 w-[100px] text-xs">
            <span className="shrink-0 text-muted-foreground">比例</span>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {ratios.map((value) => {
              const [a, b] = value.split(":").map(Number)
              const ms = 14
              const rw = a >= b ? ms : Math.round((ms * a) / b)
              const rh = b >= a ? ms : Math.round((ms * b) / a)
              return (
                <SelectItem key={value} value={value}>
                  <span className="flex items-center gap-1.5">
                    <svg
                      width="16"
                      height="16"
                      viewBox="0 0 16 16"
                      aria-hidden="true"
                      className="shrink-0 text-muted-foreground"
                    >
                      <rect
                        x={(16 - rw) / 2 + 0.5}
                        y={(16 - rh) / 2 + 0.5}
                        width={rw - 1}
                        height={rh - 1}
                        rx="1"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.5"
                      />
                    </svg>
                    {value}
                  </span>
                </SelectItem>
              )
            })}
          </SelectContent>
        </Select>
        {sizesForRatio.length > 0 ? (
          <Select value={currentRatioSize} onValueChange={(v) => v != null && setFixedSize(v)}>
            <SelectTrigger className="h-8 w-[120px] text-xs">
              <span className="shrink-0 text-muted-foreground">尺寸</span>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {sizesForRatio.map(([w, h]) => (
                <SelectItem key={`${w}x${h}`} value={`${w}x${h}`}>
                  {w}×{h}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <Select value={resolution} onValueChange={(v) => setResolution(v ?? "2K")}>
            <SelectTrigger className="h-8 w-[90px] text-xs">
              <span className="shrink-0 text-muted-foreground">分辨率</span>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {["1K", "2K", "4K"].map((r) => (
                <SelectItem key={r} value={r}>
                  {r}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </>
    )
  }

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
            <div className="flex items-center gap-2">
              <Tabs
                value={generationType}
                onValueChange={(v) => handleTypeChange(v as "image" | "video" | "voice" | "music")}
              >
                <TabsList className="h-7">
                  <TabsTrigger value="image" className="h-6 px-3 text-xs">
                    AI 生图
                  </TabsTrigger>
                  <TabsTrigger value="video" className="h-6 px-3 text-xs">
                    AI 视频
                  </TabsTrigger>
                  <TabsTrigger value="voice" className="h-6 px-3 text-xs">
                    配音生成
                  </TabsTrigger>
                  <TabsTrigger value="music" className="h-6 px-3 text-xs">
                    音乐生成
                  </TabsTrigger>
                </TabsList>
              </Tabs>
            </div>

            {/* 参考素材拖入区 */}
            {!isAudioType && (
              <ReferenceDropZone
                max={!isVideo && imageConfig?.edit ? (imageConfig.edit.maxInputImages ?? 1) : 16}
                isEditMode={isEditMode}
              />
            )}

            {/* Prompt 输入 */}
            <div className="flex min-h-0 flex-1 flex-col gap-1">
              <div className="flex items-center justify-between">
                {referenceAssets.length > 0 && isEditMode && (
                  <span className="flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 font-medium text-amber-600/80 text-xs">
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
                <div className="ml-auto">
                  <PromptTemplateDialog
                    type={isVideo ? "VIDEO" : "IMAGE"}
                    onSelect={(p) => setPrompt(p)}
                    hasReferenceImages={!isVideo && referenceAssets.length > 0}
                  />
                </div>
              </div>
              <PromptInput
                value={prompt}
                onChange={setPrompt}
                placeholder={
                  isVideo
                    ? "描述你想生成的视频内容..."
                    : generationType === "voice"
                      ? "输入需要配音的文本内容（最多 200 字）..."
                      : generationType === "music"
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
            {!isVideo && modeConfig?.negativePrompt && (
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
                  <Select
                    value={model}
                    onValueChange={(v) => setModel(v ?? imageModels[0]?.modelId ?? "")}
                  >
                    <SelectTrigger className="h-8 w-[160px] text-xs">
                      <span className="shrink-0 text-muted-foreground">模型</span>
                      <span className="truncate">
                        {(isVideo ? videoModels : imageModels).find((m) => m.modelId === model)
                          ?.displayName ?? model}
                      </span>
                    </SelectTrigger>
                    <SelectContent>
                      {(isVideo ? videoModels : imageModels).map((m) => (
                        <SelectItem key={m.modelId} value={m.modelId}>
                          {m.displayName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  {/* 图像尺寸控件 */}
                  {!isVideo && renderSizeControl()}

                  {/* 视频：比例 + 时长 */}
                  {isVideo && (
                    <>
                      <Select
                        value={aspectRatio}
                        onValueChange={(v) => setAspectRatio(v ?? "9:16")}
                      >
                        <SelectTrigger className="h-8 w-[100px] text-xs">
                          <span className="shrink-0 text-muted-foreground">比例</span>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {["1:1", "9:16", "16:9", "4:3"].map((v) => (
                            <SelectItem key={v} value={v}>
                              {v}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Select
                        value={videoDuration}
                        onValueChange={(v) => setVideoDuration(v ?? "5s")}
                      >
                        <SelectTrigger className="h-8 w-[90px] text-xs">
                          <span className="shrink-0 text-muted-foreground">时长</span>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="5s">5 秒</SelectItem>
                          <SelectItem value="10s">10 秒</SelectItem>
                          <SelectItem value="15s">15 秒</SelectItem>
                          <SelectItem value="30s">30 秒</SelectItem>
                        </SelectContent>
                      </Select>
                    </>
                  )}

                  {/* 生成张数 */}
                  {!isVideo && (modeConfig?.maxImages ?? 1) > 1 && (
                    <Select
                      value={String(imageCount)}
                      onValueChange={(v) => setImageCount(Number(v))}
                    >
                      <SelectTrigger className="h-8 w-[80px] text-xs">
                        <span className="shrink-0 text-muted-foreground">张数</span>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {Array.from({ length: modeConfig?.maxImages ?? 1 }, (_, i) => i + 1).map(
                          (n) => (
                            <SelectItem key={n} value={String(n)}>
                              {n} 张
                            </SelectItem>
                          )
                        )}
                      </SelectContent>
                    </Select>
                  )}

                  {/* 提示词改写开关 */}
                  {!isVideo && modeConfig?.promptExtend && (
                    <div className="flex items-center gap-1.5">
                      <Switch
                        id="prompt-extend"
                        checked={promptExtend}
                        onCheckedChange={setPromptExtend}
                        className="h-4 w-7"
                      />
                      <Label
                        htmlFor="prompt-extend"
                        className="cursor-pointer text-muted-foreground text-xs"
                      >
                        智能改写
                      </Label>
                    </div>
                  )}

                  {/* Seed 输入 */}
                  {!isVideo && modeConfig?.seed && (
                    <Input
                      type="number"
                      min={0}
                      max={2147483647}
                      value={seed === 0 ? "" : seed}
                      onChange={(e) => setSeed(e.target.value ? Number(e.target.value) : 0)}
                      placeholder="Seed"
                      className="h-8 w-[90px] text-xs"
                    />
                  )}

                  {/* 画质选择（选项由 modeConfig.quality 数组决定） */}
                  {!isVideo && modeConfig?.quality && modeConfig.quality.length > 0 && (
                    <Select value={quality} onValueChange={(v) => v != null && setQuality(v)}>
                      <SelectTrigger className="h-8 w-[100px] text-xs">
                        <span className="shrink-0 text-muted-foreground">画质</span>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {modeConfig.quality.map((q) => (
                          <SelectItem key={q} value={q}>
                            {{ auto: "自动", low: "低", medium: "中", high: "高" }[q] ?? q}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}

                  {/* 图片格式（选项由 modeConfig.format 数组决定） */}
                  {!isVideo && modeConfig?.format && modeConfig.format.length > 0 && (
                    <Select value={format} onValueChange={(v) => v != null && setFormat(v)}>
                      <SelectTrigger className="h-8 w-[90px] text-xs">
                        <span className="shrink-0 text-muted-foreground">格式</span>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {modeConfig.format.map((f) => (
                          <SelectItem key={f} value={f}>
                            {f.toUpperCase()}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}

                  {/* 背景模式（图像编辑接口支持） */}
                  {!isVideo && modeConfig?.background && modeConfig.background.length > 0 && (
                    <Select value={background} onValueChange={(v) => v != null && setBackground(v)}>
                      <SelectTrigger className="h-8 w-[110px] text-xs">
                        <span className="shrink-0 text-muted-foreground">背景</span>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {modeConfig.background.map((b) => (
                          <SelectItem key={b} value={b}>
                            {{ auto: "自动", transparent: "透明", opaque: "不透明" }[b] ?? b}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}

                  {/* 内容审核 */}
                  {!isVideo &&
                    modeConfig?.contentModeration &&
                    modeConfig.contentModeration.length > 0 && (
                      <Select
                        value={contentModeration}
                        onValueChange={(v) => v != null && setContentModeration(v)}
                      >
                        <SelectTrigger className="h-8 w-[100px] text-xs">
                          <span className="shrink-0 text-muted-foreground">审核</span>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {modeConfig.contentModeration.map((v) => (
                            <SelectItem key={v} value={v}>
                              {{ auto: "自动", low: "宽松" }[v] ?? v}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                </div>

                <RoleSelector value={agentRole} onChange={setAgentRole} />

                <Button
                  size="sm"
                  disabled={
                    generateImage.isPending || !prompt.trim() || prompt.length > promptMaxLength
                  }
                  onClick={handleGenerate}
                  className="bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
                >
                  {generateImage.isPending ? "生成中..." : "生成"}
                </Button>
              </div>
            </div>
          )}

          {/* 音频参数栏（配音/音乐）：音色或演唱声音 + 生成按钮 */}
          {isAudioType && (
            <div className="shrink-0 border-t px-4 py-3">
              <div className="flex flex-wrap items-center justify-center gap-2">
                {generationType === "voice" ? (
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

                {generationType === "voice" && (
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
                  className="bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
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
