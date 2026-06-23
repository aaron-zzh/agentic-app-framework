/**
 * 创作-爆款复制 4 步向导（真实接口版）
 *
 * 步骤：上传图片 → AI 分析 → 调整说明 → 生成文案 → 保存
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Check,
  ChevronRight,
  FileSearch,
  Loader2,
  Sparkles,
  Upload,
  Wand2,
  Zap
} from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Textarea } from "@/components/ui/textarea"
import { postAiStream } from "@/lib/api/ai-stream"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { notify } from "@/lib/notification"
import { cn } from "@/lib/utils/index"

type StepKey = "upload" | "analyze" | "adjust" | "generate"

const STEPS: {
  key: StepKey
  title: string
  desc: string
  icon: React.FC<{ className?: string }>
}[] = [
  { key: "upload", title: "上传爆款", desc: "上传爆款图片/截图，AI 提取内容结构", icon: Upload },
  { key: "analyze", title: "AI 分析", desc: "提取标题套路、节奏、钩子、转化点", icon: FileSearch },
  { key: "adjust", title: "调整说明", desc: "告诉 AI 你的产品/账号定位", icon: Wand2 },
  { key: "generate", title: "一键生成", desc: "输出适配你账号的复刻文案", icon: Sparkles }
]

export default function StudioCreateViralPage() {
  const [activeStep, setActiveStep] = useState(0)
  const [uploadedUrl, setUploadedUrl] = useState<string | null>(null)
  const [uploadedKey, setUploadedKey] = useState<string | null>(null)
  const [previewSrc, setPreviewSrc] = useState<string | null>(null)
  const [analysis, setAnalysis] = useState("")
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [userNotes, setUserNotes] = useState("")
  const [result, setResult] = useState("")
  const [isGenerating, setIsGenerating] = useState(false)
  const abortRef = useRef<AbortController | null>(null)

  const { upload, uploading } = useFileUpload()
  const fileInputRef = useRef<HTMLInputElement>(null)

  // 步骤 1：上传图片
  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      // 本地预览
      const preview = URL.createObjectURL(file)
      setPreviewSrc(preview)
      try {
        const result = await upload(file)
        setUploadedUrl(result.url)
        // 后端 copywriting analyze 用 content，这里存文件 URL 字符串
        setUploadedKey(result.url)
        notify.success("图片上传成功")
      } catch {
        notify.error("图片上传失败，请重试")
        setPreviewSrc(null)
      }
    },
    [upload]
  )

  // 步骤 2：AI 分析
  const handleAnalyze = useCallback(async () => {
    if (!uploadedKey) return
    setIsAnalyzing(true)
    setAnalysis("")
    abortRef.current = new AbortController()
    try {
      await postAiStream(
        "/aigc/copywriting/analyze",
        { content: uploadedKey },
        {
          onChunk: (chunk) => setAnalysis((prev) => prev + chunk),
          onDone: () => setIsAnalyzing(false),
          onError: () => {
            setIsAnalyzing(false)
            notify.error("分析失败，请重试")
          },
          signal: abortRef.current.signal
        }
      )
    } catch {
      setIsAnalyzing(false)
    }
  }, [uploadedKey])

  // 步骤 4：生成文案
  const handleGenerate = useCallback(async () => {
    setIsGenerating(true)
    setResult("")
    abortRef.current = new AbortController()
    try {
      await postAiStream(
        "/aigc/copywriting/generate",
        {
          topic: analysis,
          userNotes,
          referenceImageKeys: uploadedKey ? [uploadedKey] : undefined
        },
        {
          onChunk: (chunk) => setResult((prev) => prev + chunk),
          onDone: () => setIsGenerating(false),
          onError: () => {
            setIsGenerating(false)
            notify.error("生成失败，请重试")
          },
          signal: abortRef.current.signal
        }
      )
    } catch {
      setIsGenerating(false)
    }
  }, [analysis, userNotes, uploadedKey])

  // 跳步骤
  const goNext = useCallback(async () => {
    if (activeStep === 0 && !uploadedUrl) {
      notify.warning("请先上传图片")
      return
    }
    if (activeStep === 1 && !analysis) {
      await handleAnalyze()
    }
    if (activeStep < STEPS.length - 1) {
      setActiveStep((s) => s + 1)
    }
  }, [activeStep, uploadedUrl, analysis, handleAnalyze])

  // 保存到资产
  const handleSave = useCallback(() => {
    if (!result) return
    navigator.clipboard.writeText(result).then(() => notify.success("文案已复制到剪贴板"))
  }, [result])

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header className="flex items-end justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Zap className="size-5 text-amber-400" />
            <h1 className="font-semibold text-xl">爆款复制</h1>
            <NeonChip tone="rose" size="sm" dot>
              热门
            </NeonChip>
          </div>
          <p className="text-muted-foreground text-sm">
            上传爆款，AI 拆解套路，一键生成属于你的版本
          </p>
        </div>
      </header>

      {/* 步骤进度条 */}
      <GlassCard glow="accent">
        <div className="p-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step, idx) => {
              const isActive = idx === activeStep
              const isDone = idx < activeStep
              const Icon = step.icon
              return (
                <button
                  type="button"
                  key={step.key}
                  onClick={() => setActiveStep(idx)}
                  className={cn(
                    "relative flex flex-col gap-2 rounded-xl border p-4 text-left transition-all",
                    isActive
                      ? "border-amber-400/40 bg-amber-400/[0.06]"
                      : isDone
                        ? "border-emerald-400/30 bg-emerald-400/[0.04]"
                        : "border-foreground/[0.06] hover:border-foreground/[0.12]"
                  )}
                >
                  <div className="flex items-center gap-2">
                    <div
                      className={cn(
                        "flex size-7 shrink-0 items-center justify-center rounded-lg text-xs",
                        isDone
                          ? "bg-emerald-400/15 text-emerald-300"
                          : isActive
                            ? "bg-amber-400/15 text-amber-300"
                            : "bg-foreground/[0.06] text-muted-foreground"
                      )}
                    >
                      {isDone ? <Check className="size-3.5" /> : <Icon className="size-3.5" />}
                    </div>
                    <span className="font-medium text-xs">
                      第 {idx + 1} 步：{step.title}
                    </span>
                  </div>
                  <p className="text-muted-foreground text-xs leading-5">{step.desc}</p>
                </button>
              )
            })}
          </div>
        </div>
      </GlassCard>

      {/* 当前步骤内容 */}
      <GlassCard glow="violet">
        <div className="space-y-4 p-6">
          <div className="space-y-1">
            <p className="text-muted-foreground text-xs">
              第 {activeStep + 1} 步 / 共 {STEPS.length} 步
            </p>
            <h2 className="font-semibold text-lg">{STEPS[activeStep].title}</h2>
            <p className="text-muted-foreground text-sm">{STEPS[activeStep].desc}</p>
          </div>

          {/* 步骤 1：上传图片 */}
          {activeStep === 0 && (
            <div className="space-y-3">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFileChange}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className={cn(
                  "flex w-full flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-10 transition-colors",
                  previewSrc
                    ? "border-emerald-400/40"
                    : "border-foreground/[0.12] hover:border-foreground/[0.24]"
                )}
              >
                {uploading ? (
                  <Loader2 className="size-8 animate-spin text-amber-400" />
                ) : previewSrc ? (
                  // biome-ignore lint/performance/noImgElement: 本地 blob URL，next/image 不支持
                  <img src={previewSrc} alt="预览" className="max-h-48 rounded-lg object-contain" />
                ) : (
                  <>
                    <Upload className="size-8 text-muted-foreground/60" />
                    <p className="text-muted-foreground text-sm">点击上传爆款图片</p>
                  </>
                )}
              </button>
              {uploadedUrl && (
                <NeonChip tone="emerald" size="sm" dot>
                  图片上传成功
                </NeonChip>
              )}
            </div>
          )}

          {/* 步骤 2：AI 分析结果 */}
          {activeStep === 1 && (
            <div className="space-y-3">
              {isAnalyzing && (
                <div className="flex items-center gap-2 text-amber-400 text-sm">
                  <Loader2 className="size-4 animate-spin" />
                  <span>AI 正在分析爆款结构...</span>
                </div>
              )}
              {!analysis && !isAnalyzing && (
                <GlowButton tone="primary" size="sm" onClick={handleAnalyze}>
                  <FileSearch className="size-4" />
                  开始分析
                </GlowButton>
              )}
              {analysis && (
                <div className="max-h-60 overflow-y-auto whitespace-pre-wrap rounded-xl bg-foreground/[0.02] p-4 text-sm leading-6">
                  {analysis}
                </div>
              )}
            </div>
          )}

          {/* 步骤 3：调整说明 */}
          {activeStep === 2 && (
            <Textarea
              placeholder="请输入你的产品特点、目标用户、账号定位…（例如：我卖有机护肤品，目标用户是 25-35 岁都市女白领）"
              value={userNotes}
              onChange={(e) => setUserNotes(e.target.value)}
              className="min-h-32 bg-foreground/[0.02]"
            />
          )}

          {/* 步骤 4：生成结果 */}
          {activeStep === 3 && (
            <div className="space-y-3">
              {!result && !isGenerating && (
                <GlowButton tone="primary" size="sm" onClick={handleGenerate}>
                  <Sparkles className="size-4" />
                  立即生成
                </GlowButton>
              )}
              {isGenerating && (
                <div className="flex items-center gap-2 text-sm text-violet-400">
                  <Loader2 className="size-4 animate-spin" />
                  <span>AI 正在生成文案...</span>
                </div>
              )}
              {result && (
                <div className="space-y-3">
                  <div className="max-h-60 overflow-y-auto whitespace-pre-wrap rounded-xl bg-foreground/[0.02] p-4 text-sm leading-6">
                    {result}
                  </div>
                  <GlowButton tone="ghost" size="sm" onClick={handleSave}>
                    复制到剪贴板
                  </GlowButton>
                </div>
              )}
            </div>
          )}

          <div className="flex items-center justify-between border-foreground/[0.06] border-t pt-3">
            <GlowButton
              tone="ghost"
              size="sm"
              onClick={() => setActiveStep((s) => Math.max(0, s - 1))}
              disabled={activeStep === 0}
            >
              上一步
            </GlowButton>
            {activeStep < STEPS.length - 1 && (
              <GlowButton tone="primary" size="sm" onClick={goNext}>
                下一步
                <ChevronRight className="size-4" />
              </GlowButton>
            )}
          </div>
        </div>
      </GlassCard>
    </div>
  )
}
