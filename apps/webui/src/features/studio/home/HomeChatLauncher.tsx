/**
 * Studio 首屏-统一对话入口卡
 *
 * 工具栏两态：
 * - 默认：[文生图] [AI视频] ... 能力胶囊 | 语音 发送
 * - 选中：[当前能力 ×] [+上传] [模型▾] [参数▾] | 语音 发送
 *   - 模型▾：ModelSelector dropdown
 *   - 参数▾：Popover 内嵌 ModelParamsBar（图像）或视频参数
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation, useQueryClient } from "@tanstack/react-query"
import {
  ArrowUp,
  ChevronDown,
  Image,
  MessageCircle,
  Mic,
  Music,
  Plus,
  Sparkles,
  Type,
  Video,
  Wand2,
  X,
  Zap
} from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { AnimateBorder } from "@/components/animate/animate-border"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Skeleton } from "@/components/ui/skeleton"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { PromptInput } from "@/features/aigc/generation/PromptInput"
import { SkillPickerContent } from "@/features/aigc/generation/SkillPicker"
import { useAigcStore } from "@/features/aigc/store"
import { VOICES } from "@/features/aigc/voice-options"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { useSlotStore } from "@/features/studio/slots/store"
import { calcRatio } from "@/lib/api/rest/ai/ai-model"
import { request } from "@/lib/api/rest/entity/crud"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useAiSkills } from "@/lib/queries/use-ai-skills"
import { useChatSessions } from "@/lib/queries/use-chat"
import { useGenerateImage, useGenerateVideo } from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils"
import { ImageUploadChip } from "./ImageUploadChip"

// ─── 功能定义 ───────────────────────────────────────────────────────────────

type FeatureKey = "image" | "video" | "viral" | "voiceover" | "voice" | "music" | "redbook"

interface Feature {
  key: FeatureKey
  label: string
  icon: React.FC<{ className?: string }>
  hasUpload: boolean
  hasModel: boolean // 是否支持模型/参数选择
  hasSkill: boolean // 是否显示技能选择
  activeClass: string
}

const FEATURES: Feature[] = [
  {
    key: "image",
    label: "AI 生图",
    icon: Image,
    hasUpload: true,
    hasModel: true,
    hasSkill: true,
    activeClass: "border-violet-500/40 bg-violet-500/15 text-violet-400"
  },
  {
    key: "video",
    label: "AI 视频",
    icon: Video,
    hasUpload: true,
    hasModel: true,
    hasSkill: true,
    activeClass: "border-cyan-500/40 bg-cyan-500/15 text-cyan-400"
  },
  {
    key: "viral",
    label: "爆款复制",
    icon: Zap,
    hasUpload: true,
    hasModel: false,
    hasSkill: true,
    activeClass: "border-amber-500/40 bg-amber-500/15 text-amber-400"
  },
  {
    key: "voiceover",
    label: "口播文案",
    icon: Mic,
    hasUpload: false,
    hasModel: false,
    hasSkill: true,
    activeClass: "border-rose-500/40 bg-rose-500/15 text-rose-400"
  },
  {
    key: "voice",
    label: "配音",
    icon: Mic,
    hasUpload: false,
    hasModel: false,
    hasSkill: false,
    activeClass: "border-rose-500/40 bg-rose-500/15 text-rose-400"
  },
  {
    key: "music",
    label: "音乐",
    icon: Music,
    hasUpload: false,
    hasModel: false,
    hasSkill: false,
    activeClass: "border-emerald-500/40 bg-emerald-500/15 text-emerald-400"
  },
  {
    key: "redbook",
    label: "小红书",
    icon: Type,
    hasUpload: false,
    hasModel: false,
    hasSkill: true,
    activeClass: "border-pink-500/40 bg-pink-500/15 text-pink-400"
  }
]

// ─── 主组件 ─────────────────────────────────────────────────────────────────

export function HomeChatLauncher() {
  const [input, setInput] = useState("")
  const queryClient = useQueryClient()
  const [activeFeature, setActiveFeature] = useState<FeatureKey | null>(null)
  const [refImage, setRefImage] = useState<{
    url: string
    previewSrc: string
    name: string
  } | null>(null)
  const [uploadingChip, setUploadingChip] = useState<{ name: string; previewSrc: string } | null>(
    null
  )
  const [paramsOpen, setParamsOpen] = useState(false)
  const [skillPickerOpen, setSkillPickerOpen] = useState(false)
  const [recentTasks, setRecentTasks] = useState<AigcTaskEvent[]>([])
  const [voiceId, setVoiceId] = useState<string>(VOICES[0].value)
  const [musicGender, setMusicGender] = useState<string>("female")

  useAigcTaskStream({
    onCreated: useCallback((task: AigcTaskEvent) => {
      setRecentTasks((prev) => [task, ...prev].slice(0, 5))
    }, []),
    onProgress: useCallback((task: AigcTaskEvent) => {
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback(
      (task: AigcTaskEvent) => {
        setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
        queryClient.invalidateQueries({ queryKey: ["media-assets"] })
      },
      [queryClient]
    ),
    onFailed: useCallback((task: AigcTaskEvent) => {
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, [])
  })

  const router = useRouter()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { upload, uploading, progress } = useFileUpload()
  const { data: sessions, isLoading: sessionsLoading } = useChatSessions()
  const openSlot = useSlotStore((s) => s.openSlot)

  // 技能选择
  const selectedSkill = useAigcStore((s) => s.selectedSkill)
  const setSelectedSkill = useAigcStore((s) => s.setSelectedSkill)
  const { data: allSkills } = useAiSkills()

  // 当前 feature 对应的技能分类（用于 SkillPicker 默认筛选）
  const FEATURE_CATEGORY_MAP: Partial<Record<FeatureKey, string>> = {
    image: "IMAGE_GEN",
    video: "VIDEO_GEN",
    viral: "COPYWRITING",
    voiceover: "COPYWRITING",
    redbook: "COPYWRITING"
  }

  // 图像模型 + 参数
  const {
    options: imageOptions,
    modelId: imageModelId,
    setModelId: setImageModelId,
    currentModel: imageCurrentModel
  } = useModelSelector("IMAGE_GEN")
  const {
    params: imageParams,
    onChangeParams: onChangeImageParams,
    resolvedSize
  } = useGenerationParams(imageCurrentModel)

  // 视频模型 + 参数（简化版，复用 VIDEO_GEN 模型的 videoConfig）
  const {
    options: videoOptions,
    modelId: videoModelId,
    setModelId: setVideoModelId,
    currentModel: videoCurrentModel
  } = useModelSelector("VIDEO_GEN")
  const { params: videoParams, onChangeParams: onChangeVideoParams } =
    useGenerationParams(videoCurrentModel)

  // 视频品牌列表（按 provider 去重）
  const videoBrands = Array.from(
    videoOptions.reduce((map, o) => {
      if (!map.has(o.meta.provider)) map.set(o.meta.provider, o.meta.displayName.split(/[-\s]/)[0])
      return map
    }, new Map<string, string>())
  ).map(([provider, label]) => ({ provider, label }))

  const [selectedVideoBrand, setSelectedVideoBrand] = useState("")

  // 品牌加载后自动选第一个
  // biome-ignore lint/correctness/useExhaustiveDependencies: 仅响应品牌列表加载
  useEffect(() => {
    if (videoBrands.length > 0 && !selectedVideoBrand) {
      const first = videoBrands[0].provider
      setSelectedVideoBrand(first)
      const matched =
        videoOptions.find((o) => o.meta.provider === first && o.value.includes("t2v")) ??
        videoOptions.find((o) => o.meta.provider === first)
      if (matched) setVideoModelId(matched.value)
    }
  }, [videoBrands.length, selectedVideoBrand])

  const generateImage = useGenerateImage()
  const generateVideo = useGenerateVideo()

  const generateVoice = useMutation({
    mutationFn: (prompt: string) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({ type: "VOICE", prompt, projectId: null, params: { voice: voiceId } })
      })
  })

  const generateMusic = useMutation({
    mutationFn: (prompt: string) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MUSIC",
          prompt,
          projectId: null,
          params: { gender: musicGender }
        })
      })
  })

  const recentSessions = sessions?.slice(0, 3) ?? []
  const activeFeatureMeta = FEATURES.find((f) => f.key === activeFeature) ?? null

  const isImage = activeFeature === "image"
  const isVideo = activeFeature === "video"

  // 上传参考图
  const handleRefImageUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      const previewSrc = URL.createObjectURL(file)
      // 立刻显示上传中 chip
      setUploadingChip({ name: file.name, previewSrc })
      setRefImage(null)
      try {
        const result = await upload(file)
        setRefImage({ url: result.url, previewSrc, name: file.name })
        setUploadingChip(null)
      } catch {
        toast.error("图片上传失败")
        URL.revokeObjectURL(previewSrc)
        setUploadingChip(null)
      }
      e.target.value = ""
    },
    [upload]
  )

  // 提交
  const handleSubmit = async () => {
    const prompt = input.trim()
    if (!activeFeature) {
      toast.info("请先选择一个创作能力（图片、视频、文案等）")
      return
    }
    if (!prompt && !refImage) {
      toast.error("请输入创作描述")
      return
    }
    try {
      if (isImage) {
        const { width, height, sizePreset } = resolvedSize
        const cfg = imageCurrentModel?.imageConfig
        const taskId = await generateImage.mutateAsync({
          prompt: prompt || "参考图生成",
          model: imageModelId ?? undefined,
          width,
          height,
          sizePreset,
          aspectRatio: cfg?.mode === "ratio" ? imageParams.aspectRatio : undefined,
          imageCount: imageParams.imageCount,
          promptExtend: imageParams.promptExtend,
          quality: imageParams.quality,
          format: imageParams.format,
          imageUrls: refImage ? [refImage.url] : undefined,
          systemPrompt: selectedSkill?.systemPrompt ?? undefined
        })
        toast.success(`图像任务已提交（#${taskId}）`)
        openSlot({ panelType: "recent-tasks" })
        setInput("")
        setRefImage(null)
        setUploadingChip(null)
      } else if (isVideo) {
        const vCfg = videoCurrentModel?.videoConfig
        // 根据是否有图自动路由模式和模型
        const autoImageMode = refImage ? "FIRST_FRAME" : "T2V"
        const autoModelId = (() => {
          const suffix = refImage ? "i2v" : "t2v"
          return (
            videoOptions.find(
              (o) =>
                o.meta.provider === (videoCurrentModel?.provider ?? "") && o.value.includes(suffix)
            )?.value ?? videoModelId
          )
        })()
        const taskId = await generateVideo.mutateAsync({
          prompt: prompt || "参考图生成视频",
          model: autoModelId ?? undefined,
          imageMode: autoImageMode,
          imageUrl: refImage ? refImage.url : undefined,
          ...(vCfg?.resolutions?.length
            ? { resolution: videoParams.resolution ?? vCfg.resolutions[0] }
            : {}),
          ...(vCfg?.ratios?.length ? { ratio: videoParams.aspectRatio ?? vCfg.ratios[0] } : {}),
          systemPrompt: selectedSkill?.systemPrompt ?? undefined
        })
        toast.success(`视频任务已提交（#${taskId}）`)
        openSlot({ panelType: "recent-tasks" })
        setInput("")
        setRefImage(null)
        setUploadingChip(null)
      } else if (activeFeature === "voice") {
        if (prompt.length > 200) {
          toast.error("配音文本不超过 200 字")
          return
        }
        const taskId = await generateVoice.mutateAsync(prompt)
        toast.success(`配音任务已提交（#${taskId}）`)
        openSlot({ panelType: "recent-tasks" })
        setInput("")
      } else if (activeFeature === "music") {
        if (prompt.length > 200) {
          toast.error("音乐描述不超过 200 字")
          return
        }
        const taskId = await generateMusic.mutateAsync(prompt)
        toast.success(`音乐任务已提交（#${taskId}）`)
        openSlot({ panelType: "recent-tasks" })
        setInput("")
      } else if (activeFeature === "voiceover" || activeFeature === "redbook") {
        if (prompt) sessionStorage.setItem("aaf:launcher:prompt", prompt)
        const skillCode = selectedSkill?.code ?? activeFeature
        router.push(`/studio/create/copy?skillCode=${encodeURIComponent(skillCode)}`)
      } else {
        const skillQuery = selectedSkill
          ? `&skillCode=${encodeURIComponent(selectedSkill.code ?? "")}`
          : ""
        const safePrompt = prompt.length > 500 ? prompt.slice(0, 500) : prompt
        if (prompt.length > 500) sessionStorage.setItem("aaf:launcher:prompt", prompt)
        router.push(
          `/studio/chat?skill=${activeFeature}&prompt=${encodeURIComponent(safePrompt)}${skillQuery}`
        )
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "提交失败，请重试")
    }
  }

  const isSubmitting =
    generateImage.isPending ||
    generateVideo.isPending ||
    generateVoice.isPending ||
    generateMusic.isPending ||
    uploading
  const canSubmit = !isSubmitting && (input.trim().length > 0 || !!refImage)

  return (
    <>
      <AnimateBorder rounded="xl" borderWidth={1} duration={10} className="flex w-full">
        <GlassCard glow="violet" className="w-full">
          <div className="flex flex-col">
            {/* ── 输入框（含内嵌图片 chip） ── */}
            <div className="flex max-h-48 flex-col gap-2 overflow-y-auto px-4 pt-4 pb-1">
              {/* 图片 chip：上传中或已完成 */}
              {(uploadingChip || refImage) && (
                <ImageUploadChip
                  name={(uploadingChip ?? refImage)?.name ?? "图片"}
                  progress={refImage ? 100 : progress}
                  previewSrc={refImage?.previewSrc ?? uploadingChip?.previewSrc}
                  onRemove={() => {
                    setRefImage(null)
                    setUploadingChip(null)
                  }}
                />
              )}

              <PromptInput
                value={input}
                onChange={setInput}
                onSubmit={handleSubmit}
                placeholder={
                  activeFeature
                    ? `描述你想${activeFeatureMeta?.label}的内容...`
                    : "和助理说一句，让 AI 帮你完成创作..."
                }
                maxLength={3000}
                minHeight={80}
                className="flex-1 border-none bg-transparent shadow-none"
              />
            </div>

            {/* ── 工具栏 ── */}
            <div className="flex items-center gap-2 px-4 py-2.5">
              <div className="flex flex-1 items-center gap-1.5 overflow-x-auto">
                {activeFeature === null ? (
                  /* 默认态：能力胶囊 */
                  <>
                    {FEATURES.map((f) => (
                      <button
                        key={f.key}
                        type="button"
                        onClick={() => {
                          if (f.key === "viral") {
                            if (input.trim())
                              sessionStorage.setItem("aaf:launcher:prompt", input.trim())
                            router.push("/studio/create/viral")
                            return
                          }
                          // 口播/小红书：自动选中 code 匹配的技能
                          const matchedSkill = allSkills?.find((s) => s.code === f.key) ?? null
                          setSelectedSkill(matchedSkill)
                          setActiveFeature(f.key)
                          setParamsOpen(false)
                        }}
                        className="flex shrink-0 items-center gap-1 rounded-full border border-foreground/[0.08] px-2.5 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
                      >
                        <f.icon className="size-3" />
                        {f.label}
                      </button>
                    ))}
                    <Link
                      href="/studio/create"
                      className="flex shrink-0 items-center gap-0.5 rounded-full border border-foreground/[0.08] px-2.5 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
                    >
                      更多
                      <Sparkles className="size-3" />
                    </Link>
                  </>
                ) : (
                  /* 选中态 */
                  <>
                    {/* 当前能力（可关闭） */}
                    {activeFeatureMeta && (
                      <button
                        type="button"
                        onClick={() => {
                          setActiveFeature(null)
                          setRefImage(null)
                          setUploadingChip(null)
                          setParamsOpen(false)
                        }}
                        className={cn(
                          "flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs transition-colors",
                          activeFeatureMeta.activeClass
                        )}
                      >
                        <activeFeatureMeta.icon className="size-3" />
                        {activeFeatureMeta.label}
                        <X className="size-2.5 opacity-70" />
                      </button>
                    )}

                    {/* + 上传参考图 */}
                    {activeFeatureMeta?.hasUpload && (
                      <>
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          disabled={uploading}
                          className="flex shrink-0 items-center gap-1 rounded-full border border-foreground/[0.08] px-2.5 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
                        >
                          <Plus className="size-3" />
                          {uploading ? "上传中..." : "上传"}
                        </button>
                        <input
                          ref={fileInputRef}
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={handleRefImageUpload}
                        />
                      </>
                    )}

                    {/* 模型选择（图像 dropdown，视频按品牌 chips） */}
                    {activeFeatureMeta?.hasModel && isImage && (
                      <ModelSelector
                        variant="dropdown"
                        options={imageOptions}
                        value={imageModelId}
                        onChange={(id) => setImageModelId(id)}
                        className="h-7 shrink-0 gap-1 rounded-full border border-foreground/[0.08] px-2.5 text-muted-foreground text-xs hover:bg-foreground/[0.06]"
                      />
                    )}
                    {activeFeatureMeta?.hasModel &&
                      isVideo &&
                      videoBrands.map((brand) => (
                        <button
                          key={brand.provider}
                          type="button"
                          onClick={() => {
                            setSelectedVideoBrand(brand.provider)
                            const matched =
                              videoOptions.find(
                                (o) => o.meta.provider === brand.provider && o.value.includes("t2v")
                              ) ?? videoOptions.find((o) => o.meta.provider === brand.provider)
                            if (matched) setVideoModelId(matched.value)
                          }}
                          className={`flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs transition-colors ${
                            selectedVideoBrand === brand.provider
                              ? "border-cyan-500/40 bg-cyan-500/10 text-cyan-400"
                              : "border-foreground/[0.08] text-muted-foreground hover:bg-foreground/[0.06]"
                          }`}
                        >
                          {brand.label}
                        </button>
                      ))}

                    {/* 技能选择（hasSkill feature 支持） */}
                    {activeFeatureMeta?.hasSkill && (
                      <Popover open={skillPickerOpen} onOpenChange={setSkillPickerOpen}>
                        <PopoverTrigger
                          render={
                            <button
                              type="button"
                              className={cn(
                                "flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs transition-colors",
                                selectedSkill
                                  ? "border-primary/40 bg-primary/10 text-primary"
                                  : "border-foreground/[0.08] text-muted-foreground hover:bg-foreground/[0.06]"
                              )}
                            />
                          }
                        >
                          <Wand2 className="size-3" />
                          {selectedSkill ? selectedSkill.name : "技能"}
                        </PopoverTrigger>
                        <PopoverContent align="start" className="w-80 p-0" sideOffset={6}>
                          <SkillPickerContent
                            defaultCategory={
                              activeFeature ? FEATURE_CATEGORY_MAP[activeFeature] : null
                            }
                            onClose={() => setSkillPickerOpen(false)}
                          />
                        </PopoverContent>
                      </Popover>
                    )}

                    {/* 配音参数：音色选择 */}
                    {activeFeature === "voice" && (
                      <select
                        value={voiceId}
                        onChange={(e) => setVoiceId(e.target.value)}
                        className="h-7 shrink-0 rounded-full border border-foreground/[0.08] bg-background px-2.5 text-muted-foreground text-xs"
                      >
                        {VOICES.map((v) => (
                          <option key={v.value} value={v.value}>
                            {v.label}
                          </option>
                        ))}
                      </select>
                    )}

                    {/* 音乐参数：演唱音色 */}
                    {activeFeature === "music" && (
                      <select
                        value={musicGender}
                        onChange={(e) => setMusicGender(e.target.value)}
                        className="h-7 shrink-0 rounded-full border border-foreground/[0.08] bg-background px-2.5 text-muted-foreground text-xs"
                      >
                        <option value="female">女声</option>
                        <option value="male">男声</option>
                      </select>
                    )}

                    {/* 参数设置（popover） */}
                    {activeFeatureMeta?.hasModel && (
                      <Popover open={paramsOpen} onOpenChange={setParamsOpen}>
                        <PopoverTrigger
                          render={
                            <button
                              type="button"
                              className="flex shrink-0 items-center gap-1 rounded-full border border-foreground/[0.08] px-2.5 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
                            />
                          }
                        >
                          {(() => {
                            const p = isVideo ? videoParams : imageParams
                            const isEditMode =
                              isImage && !!refImage && !!imageCurrentModel?.imageConfig?.edit
                            const parts: string[] = []
                            if (isEditMode) parts.push("编辑")
                            if (isVideo) {
                              // 视频：清晰度 + 时长
                              const vcfg = videoCurrentModel?.videoConfig
                              const res = p.resolution ?? vcfg?.resolutions?.[0]
                              if (res) parts.push(res.toUpperCase())
                              const dur =
                                p.videoDuration ??
                                (vcfg?.durations?.[0] ? `${vcfg.durations[0]}s` : null)
                              if (dur) parts.push(dur.replace("s", " 秒"))
                            } else {
                              // 图像：尺寸
                              if (p.fixedSize) {
                                const [w, h] = p.fixedSize.split("x").map(Number)
                                parts.push(`${p.aspectRatio ?? calcRatio(w, h)} ${w}×${h}`)
                              } else if (p.sizePreset) {
                                parts.push(p.sizePreset)
                              } else if (p.aspectRatio) {
                                parts.push(p.aspectRatio)
                              }
                              if (p.imageCount) parts.push(`${p.imageCount}张`)
                              if (p.quality) {
                                const qLabel: Record<string, string> = {
                                  auto: "自动",
                                  low: "低",
                                  medium: "中",
                                  high: "高"
                                }
                                parts.push(qLabel[p.quality] ?? p.quality)
                              }
                              if (p.format) parts.push(p.format.toUpperCase())
                              if (p.background) {
                                const bgLabel: Record<string, string> = {
                                  auto: "自动",
                                  transparent: "透明",
                                  opaque: "不透明"
                                }
                                parts.push(bgLabel[p.background] ?? p.background)
                              }
                            }
                            return parts.length > 0 ? (
                              <span className="text-foreground/70">{parts.join(" · ")}</span>
                            ) : (
                              "参数"
                            )
                          })()}
                          <ChevronDown className="size-3" />
                        </PopoverTrigger>
                        <PopoverContent align="start" className="w-[560px] p-3">
                          {isImage && (
                            <ModelParamsBar
                              model={imageCurrentModel}
                              params={imageParams}
                              onChangeParams={onChangeImageParams}
                              isEditMode={!!refImage && !!imageCurrentModel?.imageConfig?.edit}
                            />
                          )}
                          {isVideo && (
                            <ModelParamsBar
                              model={videoCurrentModel}
                              params={videoParams}
                              onChangeParams={onChangeVideoParams}
                            />
                          )}
                        </PopoverContent>
                      </Popover>
                    )}
                  </>
                )}
              </div>

              {/* 右侧固定：语音 + 发送 */}
              <div className="flex shrink-0 items-center gap-1.5">
                <WsAsrButton
                  onResult={(text) => setInput(text)}
                  onInterim={(text) => setInput(text)}
                />
                <Button
                  type="button"
                  size="sm"
                  onClick={handleSubmit}
                  disabled={!canSubmit}
                  className="size-8 rounded-full p-0"
                >
                  <ArrowUp className="size-4" />
                </Button>
              </div>
            </div>

            {/* ── 最近会话 ── */}
            {(sessionsLoading || recentSessions.length > 0) && (
              <div className="space-y-1.5 border-foreground/[0.06] border-t px-4 py-3">
                <div className="flex items-center justify-between">
                  <p className="flex items-center gap-1 text-muted-foreground text-xs">
                    <MessageCircle className="size-3" />
                    最近会话
                  </p>
                  <Link href="/studio/chat" className="text-primary text-xs hover:underline">
                    全部
                  </Link>
                </div>
                {sessionsLoading ? (
                  <div className="space-y-1.5">
                    {Array.from({ length: 2 }).map((_, i) => (
                      <Skeleton key={`s-${i}`} className="h-7 w-full" />
                    ))}
                  </div>
                ) : (
                  <div className="flex gap-2 overflow-x-auto pb-0.5">
                    {recentSessions.map((s) => {
                      const sessionId = (s as { id?: string | number }).id?.toString() ?? ""
                      const title =
                        (s as { title?: string; name?: string }).title ??
                        (s as { name?: string }).name ??
                        "未命名会话"
                      return (
                        <Link
                          key={sessionId}
                          href={`/studio/chat?sessionId=${encodeURIComponent(sessionId)}`}
                          className="flex shrink-0 items-center gap-1.5 rounded-lg border border-foreground/[0.06] bg-foreground/[0.02] px-3 py-1.5 text-xs transition-colors hover:bg-foreground/[0.06]"
                        >
                          <MessageCircle className="size-3 shrink-0 opacity-50" />
                          <span className="max-w-[120px] truncate">{title}</span>
                        </Link>
                      )
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </GlassCard>
      </AnimateBorder>
      <GenerationResultCard
        tasks={recentTasks}
        mediaType={
          activeFeature === "video"
            ? "VIDEO"
            : activeFeature === "voice" || activeFeature === "music"
              ? "AUDIO"
              : "IMAGE"
        }
      />
    </>
  )
}
