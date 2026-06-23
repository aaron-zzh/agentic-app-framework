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

import { Coins, Film, ImagePlus, Video, Wand2 } from "lucide-react"
import Link from "next/link"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useEstimatedCredits } from "@/lib/hooks/use-estimated-credits"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useGenerateVideo, type VideoImageMode } from "@/lib/queries/use-image-generation"

const VIDEO_MODES: { value: VideoImageMode; label: string; icon: typeof Video }[] = [
  { value: "T2V", label: "文生视频", icon: Video },
  { value: "FIRST_FRAME", label: "图生视频", icon: ImagePlus },
  { value: "REFERENCE", label: "首尾帧", icon: Film }
]

const DURATIONS = [
  { value: "5", label: "5 秒" },
  { value: "10", label: "10 秒" },
  { value: "15", label: "15 秒" }
] as const

const RATIOS = [
  { value: "16:9", label: "16:9 横屏" },
  { value: "9:16", label: "9:16 竖屏" },
  { value: "1:1", label: "1:1 方形" }
] as const

const RESOLUTIONS = [
  { value: "720p", label: "720p" },
  { value: "1080p", label: "1080p" }
] as const

export default function StudioCreateVideoPage() {
  const [prompt, setPrompt] = useState("")
  const [mode, setMode] = useState<VideoImageMode>("T2V")
  const [duration, setDuration] = useState("5")
  const [ratio, setRatio] = useState("16:9")
  const [resolution, setResolution] = useState("720p")
  const [recentTasks, setRecentTasks] = useState<AigcTaskEvent[]>([])

  const { options, modelId, setModelId, currentModel } = useModelSelector("VIDEO_GEN")
  const generate = useGenerateVideo()

  // #19 积分预估（N5: 视频按时长倍增）
  const { credits, sufficient } = useEstimatedCredits({
    modelId,
    capability: "VIDEO_GEN",
    durationSeconds: Number(duration) || 5
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
      toast.error("生成失败，已自动退还积分")
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
        duration: Number(duration),
        ratio,
        resolution
      })
      toast.success(`视频任务已提交（#${taskId}），可在「资产-任务历史」查看进度`)
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
          <Video className="size-5 text-cyan-400" />
          <h1 className="font-semibold text-xl">AI 视频创作</h1>
        </div>
        <p className="text-muted-foreground text-sm">
          文生视频 / 图生视频 / 首尾帧，双模型可选（Happyhorse · Seedance）
        </p>
      </header>

      {/* 模式切换 */}
      <Tabs value={mode} onValueChange={(v) => setMode(v as VideoImageMode)}>
        <TabsList className="bg-foreground/[0.04]">
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
          {/* 模式提示（图生/首尾帧需上传图） */}
          {mode !== "T2V" && (
            <div className="rounded-lg border border-amber-400/20 bg-amber-400/[0.06] px-3 py-2 text-amber-300 text-xs">
              {mode === "FIRST_FRAME"
                ? "「图生视频」需要上传起始图。完整功能在项目工作台中可用，此处先做文本驱动生成。"
                : "「首尾帧」需要分别上传起始与结束图。完整功能在项目工作台中可用，此处先做文本驱动生成。"}
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
              className="min-h-[120px] resize-none border-foreground/[0.08] bg-foreground/[0.02]"
            />
          </div>

          {/* 参数 */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">模型</Label>
              <ModelSelector
                options={options}
                value={modelId}
                onChange={(id) => setModelId(id)}
                variant="select"
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">时长</Label>
              <Select value={duration} onValueChange={(v) => setDuration(v ?? "5")}>
                <SelectTrigger className="h-9">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DURATIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">画面比例</Label>
              <Select value={ratio} onValueChange={(v) => setRatio(v ?? "16:9")}>
                <SelectTrigger className="h-9">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RATIOS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-muted-foreground text-xs">分辨率</Label>
              <Select value={resolution} onValueChange={(v) => setResolution(v ?? "720p")}>
                <SelectTrigger className="h-9">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RESOLUTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* 提交栏 */}
          <div className="flex items-center justify-between border-foreground/[0.06] border-t pt-3">
            <div className="flex items-center gap-2 text-muted-foreground text-xs">
              <span>当前模型：</span>
              {currentModel ? (
                <NeonChip tone="cyan" size="sm">
                  {currentModel.displayName}
                </NeonChip>
              ) : (
                <span className="opacity-50">—</span>
              )}
            </div>

            {/* #19 积分预估 + 按钮 */}
            <div className="flex flex-col items-end gap-1">
              <Tooltip>
                <TooltipTrigger>
                  <GlowButton
                    tone="primary"
                    size="default"
                    onClick={handleSubmit}
                    disabled={generate.isPending || !prompt.trim() || !modelId}
                  >
                    <Wand2 className="size-4" />
                    {generate.isPending ? "提交中..." : "立即生成"}
                  </GlowButton>
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
