/**
 * 创作-图像 Composer——文生图最小可用版
 *
 * 多模型选择（IMAGE_GEN capability，含国内外）+ Prompt + 比例 + 数量 + 生成
 * 后端复用 /aigc/tasks/submit （type=IMAGE），任务进度通过任务面板查看
 *
 * Sprint 4 交互深化（AAF-100 #18/#19）：
 * - #18 生成结果卡片（GenerationResultCard）+ 一键保存到项目
 * - #19 积分预估悬浮 + 失败 toast（useEstimatedCredits）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Coins, Sparkles, Wand2 } from "lucide-react"
import Link from "next/link"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { ModelParamsBar } from "@/components/common/ModelParamsBar"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useEstimatedCredits } from "@/lib/hooks/use-estimated-credits"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useGenerateImage } from "@/lib/queries/use-image-generation"

export default function StudioCreateImagePage() {
  const [prompt, setPrompt] = useState("")
  const [recentTasks, setRecentTasks] = useState<AigcTaskEvent[]>([])

  const { options, modelId, setModelId, currentModel } = useModelSelector("IMAGE_GEN")
  const { params, onChangeParams, resolvedSize } = useGenerationParams(currentModel)
  const generate = useGenerateImage()

  // #19 积分预估
  const { credits, sufficient } = useEstimatedCredits({
    modelId,
    capability: "IMAGE_GEN",
    count: params.imageCount ?? 1
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
      toast.error("生成失败，已自动退还积分")
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
        imageCount: params.imageCount,
        seed: params.seed && params.seed > 0 ? params.seed : undefined,
        promptExtend: params.promptExtend,
        quality: params.quality,
        format: params.format,
        background: params.background,
        contentModeration: params.contentModeration,
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
        <p className="text-muted-foreground text-sm">
          多模型支持，文生图与图像编辑，参数可调
        </p>
      </header>

      {/* Composer 卡 */}
      <GlassCard glow="violet">
        <div className="space-y-4 p-5">
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
              className="min-h-[120px] resize-none border-foreground/[0.08] bg-foreground/[0.02]"
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
            <div className="flex items-center gap-2 text-muted-foreground text-xs">
              <span>当前模型：</span>
              {currentModel ? (
                <NeonChip tone="violet" size="sm">
                  {currentModel.displayName}
                </NeonChip>
              ) : (
                <span className="opacity-50">—</span>
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
                  {credits !== null ? (
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
              {credits !== null && !sufficient && (
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
