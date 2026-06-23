/**
 * 创作-视频 Composer——文/图/首尾帧生视频最小可用版
 *
 * 三种模式：T2V 文生视频 / FIRST_FRAME 图生视频 / REFERENCE 参考图视频
 * 多模型支持：Happyhorse / Seedance（VIDEO_GEN capability 自动加载）
 * 后端：/aigc/tasks/submit type=VIDEO
 *
 * Sprint 4 交互深化（AAF-100 #18/#19）：
 * - #18 生成结果卡片（GenerationResultCard）+ 一键保存到项目
 * - #19 积分预估悬浮 + 失败 toast（useEstimatedCredits）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Coins, Film, ImagePlus, Loader2, Video, Wand2, X } from "lucide-react"
import Link from "next/link"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useGenerateVideo, type VideoImageMode } from "@/lib/queries/use-image-generation"

const VIDEO_MODES: { value: VideoImageMode; label: string; icon: typeof Video }[] = [
  { value: "T2V", label: "文生视频", icon: Video },
  { value: "FIRST_FRAME", label: "图生视频", icon: ImagePlus },
  { value: "REFERENCE", label: "首尾帧", icon: Film }
]

const MODE_CONFIG_KEY: Record<VideoImageMode, string> = {
  T2V: "t2v",
  FIRST_FRAME: "i2v",
  REFERENCE: "r2v"
}

export default function StudioCreateVideoPage() {
  const [prompt, setPrompt] = useState("")
  const [mode, setMode] = useState<VideoImageMode>("T2V")
  const [recentTasks, setRecentTasks] = useState<AigcTaskEvent[]>([])
  const [selectedBrand, setSelectedBrand] = useState<string>("")
  const [refImageUrl, setRefImageUrl] = useState<string | null>(null)
  const [refPreview, setRefPreview] = useState<string | null>(null)
  const [lastFrameUrl, setLastFrameUrl] = useState<string | null>(null)
  const [lastFramePreview, setLastFramePreview] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const lastFrameInputRef = useRef<HTMLInputElement>(null)
  const { upload, uploading } = useFileUpload()

  const { options, modelId, setModelId, currentModel } = useModelSelector("VIDEO_GEN")
  const { params, onChangeParams } = useGenerationParams(currentModel)
  const generate = useGenerateVideo()

  // 从 options 提取品牌列表（按 provider 去重，用该 provider 代表模型的 displayName 作标签）
  const brands: { provider: string; label: string }[] = Array.from(
    options
      .reduce((map, o) => {
        if (!map.has(o.meta.provider)) map.set(o.meta.provider, o.meta.displayName)
        return map
      }, new Map<string, string>())
      .entries()
  ).map(([provider, label]) => ({ provider, label }))

  // 选品牌或切换模式时自动匹配 model
  const handleBrandOrModeChange = useCallback(
    (brand: string, newMode: VideoImageMode) => {
      const configKey = MODE_CONFIG_KEY[newMode]
      // 优先找 modelId 中包含 configKey 后缀的模型（如 t2v/i2v/r2v），再降级到 modes 字段匹配
      const matched =
        options.find((o) => o.meta.provider === brand && o.value.includes(configKey)) ??
        options.find(
          (o) => o.meta.provider === brand && o.meta.videoConfig?.modes?.includes(configKey)
        ) ??
        options.find((o) => o.meta.provider === brand)
      if (matched) setModelId(matched.value)
    },
    [options, setModelId]
  )

  // options 加载后自动选第一个品牌
  // biome-ignore lint/correctness/useExhaustiveDependencies: 仅响应品牌列表加载，避免循环触发
  useEffect(() => {
    if (brands.length > 0 && !selectedBrand) {
      const first = brands[0].provider
      setSelectedBrand(first)
      handleBrandOrModeChange(first, mode)
    }
  }, [brands.length, selectedBrand])

  const handleBrandChange = (brand: string) => {
    setSelectedBrand(brand)
    handleBrandOrModeChange(brand, mode)
  }

  const handleModeChange = (newMode: VideoImageMode) => {
    setMode(newMode)
    if (selectedBrand) handleBrandOrModeChange(selectedBrand, newMode)
  }

  const { credits, sufficient } = useEstimateAigcCredits({
    type: "VIDEO",
    model: modelId,
    prompt,
    params: {
      duration: Number(params.videoDuration?.replace("s", "")) || undefined,
      resolution: params.resolution,
      ratio: params.aspectRatio
    }
  })

  // #18 SSE 监听任务完成/失败
  useAigcTaskStream({
    onCreated: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VIDEO") return
      setRecentTasks((prev) => [task, ...prev].slice(0, 5))
    }, []),
    onProgress: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VIDEO") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VIDEO") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    // #19 失败 toast（后端已自动 refund，AAF-099）
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VIDEO") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.error("生成失败")
    }, [])
  })

  const handleSubmit = async () => {
    const trimmed = prompt.trim()
    if (!trimmed) {
      toast.error("请输入视频描述")
      return
    }
    if (!modelId) {
      toast.error("请先选择模型")
      return
    }
    try {
      const taskId = await generate.mutateAsync({
        prompt: trimmed,
        model: modelId,
        imageMode: mode,
        imageUrl: mode !== "REFERENCE" ? (refImageUrl ?? undefined) : undefined,
        referenceImageUrls:
          mode === "REFERENCE"
            ? ([refImageUrl, lastFrameUrl].filter(Boolean) as string[])
            : undefined,
        duration: Number(params.videoDuration?.replace("s", "")) || undefined,
        ratio: params.aspectRatio,
        resolution: params.resolution
      })
      toast.success(`视频任务已提交（#${taskId}），可在「资产-任务历史」查看进度`)
      setPrompt("")
      setRefImageUrl(null)
      setRefPreview(null)
      setLastFrameUrl(null)
      setLastFramePreview(null)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "提交失败，请重试")
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      {/* 标题区 */}
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <Video className="size-5 text-cyan-400" />
          <h1 className="font-semibold text-xl">AI 视频创作</h1>
        </div>
        <p className="text-muted-foreground text-sm">可生成最长 15 秒、分辨率为 1080P 的视频</p>
      </header>

      {/* 模式切换 */}
      <Tabs value={mode} onValueChange={(v) => handleModeChange(v as VideoImageMode)}>
        <TabsList className="bg-foreground/4">
          {VIDEO_MODES.map((m) => (
            <TabsTrigger key={m.value} value={m.value} className="gap-1.5">
              <m.icon className="size-3.5" />
              {m.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {/* Composer */}
      <GlassCard glow="cyan">
        <div className="space-y-4 p-5">
          {/* 图生视频 / 首尾帧：上传参考图 */}
          {mode !== "T2V" && (
            <div className="space-y-2">
              <Label className="text-muted-foreground text-xs">
                {mode === "FIRST_FRAME" ? "起始图" : "首帧 / 尾帧"}
              </Label>
              <div className="flex items-center gap-3">
                {/* 首帧（两种模式共用） */}
                <div className="flex flex-col items-center gap-1">
                  {mode === "REFERENCE" && (
                    <span className="text-[10px] text-muted-foreground">首帧</span>
                  )}
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={async (e) => {
                      const file = e.target.files?.[0]
                      if (!file) return
                      const preview = URL.createObjectURL(file)
                      setRefPreview(preview)
                      try {
                        const r = await upload(file)
                        setRefImageUrl(r.url)
                      } catch {
                        toast.error("上传失败")
                        setRefPreview(null)
                      }
                      e.target.value = ""
                    }}
                  />
                  {refPreview ? (
                    <div className="group relative size-14 overflow-hidden rounded-md border bg-muted">
                      {/* biome-ignore lint/performance/noImgElement: thumbnail */}
                      <img src={refPreview} alt="首帧" className="size-full object-cover" />
                      <button
                        type="button"
                        onClick={() => {
                          setRefPreview(null)
                          setRefImageUrl(null)
                        }}
                        className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-secondary opacity-0 shadow transition-opacity group-hover:opacity-100"
                      >
                        <X className="size-3" />
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={uploading}
                      className="flex size-14 items-center justify-center rounded-md border border-dashed text-muted-foreground transition-colors hover:border-primary hover:text-primary disabled:opacity-50"
                    >
                      {uploading ? (
                        <Loader2 className="size-4 animate-spin" />
                      ) : (
                        <ImagePlus className="size-5" />
                      )}
                    </button>
                  )}
                </div>

                {/* 尾帧（仅 REFERENCE 模式） */}
                {mode === "REFERENCE" && (
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-[10px] text-muted-foreground">尾帧</span>
                    <input
                      ref={lastFrameInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0]
                        if (!file) return
                        const preview = URL.createObjectURL(file)
                        setLastFramePreview(preview)
                        try {
                          const r = await upload(file)
                          setLastFrameUrl(r.url)
                        } catch {
                          toast.error("上传失败")
                          setLastFramePreview(null)
                        }
                        e.target.value = ""
                      }}
                    />
                    {lastFramePreview ? (
                      <div className="group relative size-14 overflow-hidden rounded-md border bg-muted">
                        {/* biome-ignore lint/performance/noImgElement: thumbnail */}
                        <img src={lastFramePreview} alt="尾帧" className="size-full object-cover" />
                        <button
                          type="button"
                          onClick={() => {
                            setLastFramePreview(null)
                            setLastFrameUrl(null)
                          }}
                          className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-secondary opacity-0 shadow transition-opacity group-hover:opacity-100"
                        >
                          <X className="size-3" />
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => lastFrameInputRef.current?.click()}
                        disabled={uploading}
                        className="flex size-14 items-center justify-center rounded-md border border-dashed text-muted-foreground transition-colors hover:border-primary hover:text-primary disabled:opacity-50"
                      >
                        {uploading ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <ImagePlus className="size-5" />
                        )}
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Prompt */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">视频描述</Label>
              <PromptTemplateDialog type="VIDEO_GEN" onSelect={(p) => setPrompt(p)} />
            </div>
            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="例如：一只海豚跃出海面，慢动作镜头，金色夕阳，电影感"
              className="max-h-[240px] min-h-[120px] resize-none overflow-y-auto border-foreground/[0.08] bg-foreground/[0.02]"
            />
          </div>

          {/* 参数行 */}
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">品牌</Label>
              <div className="flex flex-wrap gap-2">
                {brands.map((brand) => (
                  <button
                    key={brand.provider}
                    type="button"
                    onClick={() => handleBrandChange(brand.provider)}
                    className={`flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs transition-colors ${
                      selectedBrand === brand.provider
                        ? "border-cyan-500/40 bg-cyan-500/10 text-cyan-400"
                        : "border-foreground/[0.08] text-muted-foreground hover:bg-foreground/[0.06]"
                    }`}
                  >
                    {brand.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">生成参数</Label>
              <ModelParamsBar
                model={currentModel}
                params={params}
                onChangeParams={onChangeParams}
              />
            </div>
          </div>

          {/* 提交栏 */}
          <div className="flex items-center justify-between border-foreground/[0.06] border-t pt-3">
            <div className="flex items-center gap-3 text-muted-foreground text-xs">
              <div className="flex items-center gap-2">
                <span>当前模型：</span>
                {currentModel ? (
                  <NeonChip tone="cyan" size="sm">
                    {currentModel.displayName}
                  </NeonChip>
                ) : (
                  <span className="opacity-50">—</span>
                )}
              </div>
              {credits !== null && credits > 0 ? (
                <span
                  className={`flex items-center gap-1 ${!sufficient ? "text-amber-400" : "text-muted-foreground"}`}
                >
                  <Coins className="size-3" />
                  {credits} 积分
                </span>
              ) : (
                <span className="opacity-40">费用以后台为准</span>
              )}
            </div>

            {/* #19 积分预估 + 按钮 */}
            <div className="flex flex-col items-end gap-1">
              <Tooltip>
                <TooltipTrigger
                  render={
                    <GlowButton
                      tone="primary"
                      size="default"
                      onClick={handleSubmit}
                      disabled={generate.isPending || !prompt.trim() || !modelId}
                    />
                  }
                >
                  <Wand2 className="size-4" />
                  {generate.isPending ? "提交中..." : "立即生成"}
                </TooltipTrigger>
                <TooltipContent side="top">
                  {credits !== null && credits > 0 ? (
                    <span className="flex items-center gap-1">
                      <Coins className="size-3.5" />
                      预估消耗 {credits} 积分
                    </span>
                  ) : (
                    "费用以后台为准"
                  )}
                </TooltipContent>
              </Tooltip>

              {/* #19 余额不足软提示 */}
              {credits !== null && credits > 0 && !sufficient && (
                <Link href="/studio/me/credits" className="text-amber-400 text-xs hover:underline">
                  余额不足，请充值
                </Link>
              )}
            </div>
          </div>
        </div>
      </GlassCard>

      {/* #18 生成结果卡片 */}
      <GenerationResultCard tasks={recentTasks} mediaType="VIDEO" />

      {/* 引导：进入项目工作台 */}
      <GlassCard glow="none" className="border border-foreground/[0.06]">
        <div className="flex items-start gap-3 p-4">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-violet-400/10 text-violet-300">
            <Film className="size-4" />
          </div>
          <div className="flex-1 space-y-1">
            <p className="font-medium text-sm">需要参考图、首尾帧上传、视频时间线编辑？</p>
            <p className="text-muted-foreground text-xs">进入项目工作台获得完整视频创作工作流</p>
          </div>
          <Link href="/studio/projects">
            <GlowButton tone="ghost" size="sm">
              进入项目
            </GlowButton>
          </Link>
        </div>
      </GlassCard>
    </div>
  )
}
