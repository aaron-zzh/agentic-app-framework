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

import {
  ArrowUp,
  ChevronDown,
  Image,
  MessageCircle,
  Mic,
  Plus,
  Sparkles,
  Type,
  Video,
  X,
  Zap
} from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Skeleton } from "@/components/ui/skeleton"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useChatSessions } from "@/lib/queries/use-chat"
import { useGenerateImage, useGenerateVideo } from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils"

// ─── 功能定义 ───────────────────────────────────────────────────────────────

type FeatureKey = "image" | "video" | "viral" | "voiceover" | "redbook"

interface Feature {
  key: FeatureKey
  label: string
  icon: React.FC<{ className?: string }>
  hasUpload: boolean
  hasModel: boolean   // 是否支持模型/参数选择
  activeClass: string
}

const FEATURES: Feature[] = [
  {
    key: "image",
    label: "文生图",
    icon: Image,
    hasUpload: true,
    hasModel: true,
    activeClass: "border-violet-500/40 bg-violet-500/15 text-violet-400"
  },
  {
    key: "video",
    label: "AI 视频",
    icon: Video,
    hasUpload: true,
    hasModel: true,
    activeClass: "border-cyan-500/40 bg-cyan-500/15 text-cyan-400"
  },
  {
    key: "viral",
    label: "爆款复制",
    icon: Zap,
    hasUpload: true,
    hasModel: false,
    activeClass: "border-amber-500/40 bg-amber-500/15 text-amber-400"
  },
  {
    key: "voiceover",
    label: "口播文案",
    icon: Mic,
    hasUpload: false,
    hasModel: false,
    activeClass: "border-rose-500/40 bg-rose-500/15 text-rose-400"
  },
  {
    key: "redbook",
    label: "小红书",
    icon: Type,
    hasUpload: false,
    hasModel: false,
    activeClass: "border-pink-500/40 bg-pink-500/15 text-pink-400"
  }
]

// ─── 主组件 ─────────────────────────────────────────────────────────────────

export function HomeChatLauncher() {
  const [input, setInput] = useState("")
  const [activeFeature, setActiveFeature] = useState<FeatureKey | null>(null)
  const [refImage, setRefImage] = useState<{ url: string; previewSrc: string } | null>(null)
  const [paramsOpen, setParamsOpen] = useState(false)

  const router = useRouter()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const { upload, uploading } = useFileUpload()
  const { data: sessions, isLoading: sessionsLoading } = useChatSessions()

  // 图像模型 + 参数
  const {
    options: imageOptions,
    modelId: imageModelId,
    setModelId: setImageModelId,
    currentModel: imageCurrentModel
  } = useModelSelector("IMAGE_GEN")
  const { params: imageParams, onChangeParams: onChangeImageParams, resolvedSize } = useGenerationParams(imageCurrentModel)

  // 视频模型 + 参数（简化版，复用 VIDEO_GEN 模型的 videoConfig）
  const {
    options: videoOptions,
    modelId: videoModelId,
    setModelId: setVideoModelId,
    currentModel: videoCurrentModel
  } = useModelSelector("VIDEO_GEN")
  const { params: videoParams, onChangeParams: onChangeVideoParams } = useGenerationParams(videoCurrentModel)

  const generateImage = useGenerateImage()
  const generateVideo = useGenerateVideo()

  const recentSessions = sessions?.slice(0, 3) ?? []
  const activeFeatureMeta = FEATURES.find((f) => f.key === activeFeature) ?? null

  const isImage = activeFeature === "image"
  const isVideo = activeFeature === "video"

  // 上传参考图
  const handleRefImageUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      const preview = URL.createObjectURL(file)
      try {
        const result = await upload(file)
        setRefImage({ url: result.url, previewSrc: preview })
      } catch {
        toast.error("图片上传失败")
        URL.revokeObjectURL(preview)
      }
      e.target.value = ""
    },
    [upload]
  )

  // 提交
  const handleSubmit = async () => {
    const prompt = input.trim()
    if (!activeFeature) {
      if (!prompt) return
      router.push(`/studio/chat?prompt=${encodeURIComponent(prompt)}`)
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
          imageUrls: refImage ? [refImage.url] : undefined
        })
        toast.success(`图像任务已提交（#${taskId}）`)
        setInput("")
        setRefImage(null)
      } else if (isVideo) {
        const vCfg = videoCurrentModel?.videoConfig
        const taskId = await generateVideo.mutateAsync({
          prompt: prompt || "参考图生成视频",
          model: videoModelId ?? undefined,
          imageUrl: refImage ? refImage.url : undefined,
          ...(vCfg?.resolutions?.length ? { resolution: videoParams.resolution ?? vCfg.resolutions[0] } : {}),
          ...(vCfg?.ratios?.length ? { ratio: videoParams.aspectRatio ?? vCfg.ratios[0] } : {})
        })
        toast.success(`视频任务已提交（#${taskId}）`)
        setInput("")
        setRefImage(null)
      } else {
        router.push(`/studio/chat?skill=${activeFeature}&prompt=${encodeURIComponent(prompt)}`)
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "提交失败，请重试")
    }
  }

  const isSubmitting = generateImage.isPending || generateVideo.isPending || uploading
  const canSubmit = !isSubmitting && (input.trim().length > 0 || !!refImage)

  return (
    <GlassCard glow="violet">
      <div className="flex flex-col">
        {/* ── 参考图预览 ── */}
        {refImage && (
          <div className="px-4 pt-3">
            <div className="relative inline-block">
              {/* biome-ignore lint/performance/noImgElement: thumbnail */}
              <img
                src={refImage.previewSrc}
                alt="参考图"
                className="size-14 rounded-lg border border-foreground/10 object-cover"
              />
              <button
                type="button"
                onClick={() => setRefImage(null)}
                className="absolute -right-1.5 -top-1.5 flex size-4 items-center justify-center rounded-full bg-background/90 ring-1 ring-foreground/20"
              >
                <X className="size-2.5" />
              </button>
            </div>
          </div>
        )}

        {/* ── 输入框 ── */}
        <div className="flex items-start gap-3 px-4 pt-4 pb-1">
          <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary">
            <Sparkles className="size-4" />
          </div>
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault()
                handleSubmit()
              }
            }}
            placeholder={
              activeFeature
                ? `描述你想${activeFeatureMeta?.label}的内容...`
                : "和助理说一句，让 AI 帮你完成创作..."
            }
            rows={1}
            className="min-h-[28px] flex-1 resize-none bg-transparent text-base leading-relaxed outline-none placeholder:text-muted-foreground/60"
            style={{ maxHeight: 120, overflowY: "auto" }}
          />
        </div>

        {/* ── 工具栏 ── */}
        <div className="flex items-center gap-2 px-4 py-2.5">
          <div className="flex flex-1 items-center gap-1.5 overflow-x-auto">
            {activeFeature === null ? (
              /* 默认态：能力胶囊 */
              FEATURES.map((f) => (
                <button
                  key={f.key}
                  type="button"
                  onClick={() => {
                    setActiveFeature(f.key)
                    setParamsOpen(false)
                    textareaRef.current?.focus()
                  }}
                  className="flex shrink-0 items-center gap-1 rounded-full border border-foreground/[0.08] px-2.5 py-1 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
                >
                  <f.icon className="size-3" />
                  {f.label}
                </button>
              ))
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

                {/* 模型选择（dropdown） */}
                {activeFeatureMeta?.hasModel && (
                  <ModelSelector
                    variant="dropdown"
                    options={isImage ? imageOptions : videoOptions}
                    value={isImage ? imageModelId : videoModelId}
                    onChange={(id) => isImage ? setImageModelId(id) : setVideoModelId(id)}
                    className="h-7 shrink-0 gap-1 rounded-full border border-foreground/[0.08] px-2.5 text-muted-foreground text-xs hover:bg-foreground/[0.06]"
                  />
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
                      参数
                      <ChevronDown className="size-3" />
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-auto min-w-[280px] p-3">
                      {isImage && (
                        <ModelParamsBar
                          model={imageCurrentModel}
                          params={imageParams}
                          onChangeParams={onChangeImageParams}
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
              onResult={(text) => setInput((prev) => (prev ? `${prev} ${text}` : text))}
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
  )
}
