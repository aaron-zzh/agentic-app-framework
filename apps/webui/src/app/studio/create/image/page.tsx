/**
 * 创作-图像 Composer——文生图 + 图生图（参考图编辑模式）
 *
 * 多模型选择（IMAGE_GEN capability，含国内外）+ Prompt + 比例 + 数量 + 生成
 * 有参考图时自动切换为图像编辑模式（传 imageUrls 给后端）
 * 后端复用 /aigc/tasks/submit （type=IMAGE），任务进度通过任务面板查看
 *
 * Sprint 4 交互深化（AAF-100 #18/#19）：
 * - #18 生成结果卡片（GenerationResultCard）+ 一键保存到项目
 * - #19 积分预估悬浮 + 失败 toast（useEstimatedCredits）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Coins, Loader2, Plus, Sparkles, Wand2, X } from "lucide-react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { API_ORIGIN } from "@/lib/api/config"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils/cn"

export default function StudioCreateImagePage() {
  const searchParams = useSearchParams()
  const [prompt, setPrompt] = useState(() => searchParams.get("prompt") ?? "")
  const [recentTasks, setRecentTasks] = useState<AigcTaskEvent[]>([])

  const { options, modelId, setModelId, currentModel } = useModelSelector("IMAGE_GEN")
  const { params, onChangeParams, resolvedSize } = useGenerationParams(currentModel)
  const generate = useGenerateImage()

  // 参考图：本地 state，{ url, name } 列表
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { upload, uploading } = useFileUpload()
  const [refImages, setRefImages] = useState<{ url: string; name: string }[]>(() => {
    const refUrl = searchParams.get("refUrl")
    return refUrl ? [{ url: refUrl, name: "参考图" }] : []
  })
  const maxRefImages = currentModel?.imageConfig?.edit?.maxInputImages ?? 1
  const isEditMode = refImages.length > 0 && !!currentModel?.imageConfig?.edit

  async function handleRefFiles(files: FileList | null) {
    if (!files) return
    if (refImages.length >= maxRefImages) {
      toast.error(`最多 ${maxRefImages} 张参考图`)
      return
    }
    for (const file of Array.from(files).slice(0, maxRefImages - refImages.length)) {
      if (!file.type.startsWith("image/")) continue
      try {
        const result = await upload(file)
        const url = result.url.startsWith("http") ? result.url : `${API_ORIGIN}${result.url}`
        setRefImages((prev) => [...prev, { url, name: file.name }])
      } catch {
        toast.error(`${file.name} 上传失败`)
      }
    }
  }

  // #19 积分预估
  const { credits, sufficient } = useEstimateAigcCredits({
    type: "IMAGE",
    model: modelId,
    prompt,
    params: {
      imageCount: params.imageCount ?? 1,
      width: resolvedSize.width,
      height: resolvedSize.height,
      quality: params.quality
    }
  })

  // #18 SSE 监听任务完成/失败，更新结果卡
  useAigcTaskStream({
    onCreated: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE") return
      setRecentTasks((prev) => [task, ...prev].slice(0, 5))
    }, []),
    onProgress: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    // #19 失败 toast（后端已自动 refund，AAF-099）
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE") return
      setRecentTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, [])
  })

  const handleSubmit = async () => {
    const trimmed = prompt.trim()
    if (!trimmed) {
      toast.error("请输入创作提示词")
      return
    }
    if (!modelId) {
      toast.error("请先选择模型")
      return
    }
    const { width, height, sizePreset } = resolvedSize
    const cfg = currentModel?.imageConfig
    try {
      const taskId = await generate.mutateAsync({
        prompt: trimmed,
        displayPrompt: trimmed,
        model: modelId,
        width,
        height,
        sizePreset,
        aspectRatio: cfg?.mode === "ratio" ? params.aspectRatio : undefined,
        imageUrls: isEditMode ? refImages.map((r) => r.url) : undefined,
        imageCount: params.imageCount,
        seed: params.seed && params.seed > 0 ? params.seed : undefined,
        promptExtend: params.promptExtend,
        quality: params.quality,
        format: params.format,
        background: params.background,
        contentModeration: params.contentModeration
      })
      toast.success(`任务已提交（#${taskId}），可在「资产-任务历史」查看进度`)
      setPrompt("")
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "提交失败，请重试")
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      {/* 标题区 */}
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <Sparkles className="size-5 text-violet-400" />
          <h1 className="font-semibold text-xl">AI 图像创作</h1>
        </div>
        <p className="text-muted-foreground text-sm">多模型支持，文生图与图像编辑，参数可调</p>
      </header>

      {/* Composer 卡 */}
      <GlassCard glow="violet">
        <div className="space-y-4 p-5">
          {/* 参考图 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">
                参考图
                {isEditMode && (
                  <span className="ml-2 rounded-full bg-amber-500/10 px-2 py-0.5 font-medium text-[11px] text-amber-600/80">
                    图像编辑模式
                  </span>
                )}
              </Label>
              <span className="text-muted-foreground text-xs">
                {refImages.length}/{maxRefImages}
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {refImages.map((img, i) => (
                <div
                  key={`${img.url}-${i}`}
                  className="group relative size-14 overflow-hidden rounded-md border bg-muted"
                >
                  {/* biome-ignore lint/performance/noImgElement: 动态参考图 */}
                  <img src={img.url} alt={img.name} className="size-full object-cover" />
                  <button
                    type="button"
                    onClick={() => setRefImages((prev) => prev.filter((_, idx) => idx !== i))}
                    className="absolute -top-1 -right-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover:flex"
                  >
                    <X className="size-3" />
                  </button>
                </div>
              ))}
              {refImages.length < maxRefImages && (
                <button
                  type="button"
                  disabled={uploading}
                  onClick={() => fileInputRef.current?.click()}
                  className={cn(
                    "flex size-14 items-center justify-center rounded-md border border-dashed text-muted-foreground",
                    "transition-colors hover:border-primary hover:text-primary disabled:opacity-50"
                  )}
                >
                  {uploading ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Plus className="size-5" />
                  )}
                </button>
              )}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                multiple
                className="hidden"
                onChange={(e) => {
                  handleRefFiles(e.target.files)
                  if (fileInputRef.current) fileInputRef.current.value = ""
                }}
              />
            </div>
          </div>

          {/* Prompt */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">创作提示词</Label>
              <PromptTemplateDialog type="IMAGE_GEN" onSelect={(p) => setPrompt(p)} />
            </div>
            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="例如：一只穿宇航服的猫漂浮在星空中，赛博朋克风格"
              className="max-h-[240px] min-h-[120px] resize-none overflow-y-auto border-foreground/[0.08] bg-foreground/[0.02]"
            />
          </div>

          {/* 参数行 */}
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">模型</Label>
              <ModelSelector
                options={options}
                value={modelId}
                onChange={(id) => setModelId(id)}
                variant="select"
                placeholder="选择模型"
              />
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
                  <NeonChip tone="violet" size="sm">
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
                      tone="violet"
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
      <GenerationResultCard tasks={recentTasks} mediaType="IMAGE" />

      {/* 提示卡：完整版引导 */}
      <GlassCard glow="none" className="border border-foreground/[0.06]">
        <div className="flex items-start gap-3 p-4">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-cyan-400/10 text-cyan-300">
            <Sparkles className="size-4" />
          </div>
          <div className="flex-1 space-y-1">
            <p className="font-medium text-sm">想要图像编辑、首尾帧、参考图、风格预设？</p>
            <p className="text-muted-foreground text-xs">
              进入项目工作台获得完整创作能力，作品自动归档可复用
            </p>
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
