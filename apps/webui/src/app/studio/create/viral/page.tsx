/**
 * 创作-爆款复制 4 步向导
 *
 * 步骤：输入爆款内容 → AI 分析结构 → 调整说明 → 生成文案
 *
 * 后端接口：
 * - /aigc/copywriting/analyze  { content }     → 分析爆款套路（SSE）
 * - /aigc/copywriting/generate { topic, userNotes } → 生成复刻文案（SSE）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Check,
  ChevronRight,
  FileSearch,
  FileText,
  Loader2,
  Sparkles,
  Wand2,
  Zap
} from "lucide-react"
import { useSearchParams } from "next/navigation"
import { useCallback, useRef, useState } from "react"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Textarea } from "@/components/ui/textarea"
import { postAiStream } from "@/lib/api/ai-stream"
import { notify } from "@/lib/notification"
import { useCreateDocument } from "@/lib/queries/use-documents"
import { cn } from "@/lib/utils/index"

type StepKey = "input" | "analyze" | "adjust" | "generate"

const STEPS: {
  key: StepKey
  title: string
  desc: string
  icon: React.FC<{ className?: string }>
}[] = [
  { key: "input", title: "输入爆款", desc: "粘贴爆款文案、标题或内容描述", icon: FileText },
  { key: "analyze", title: "AI 分析", desc: "提取标题套路、节奏、钩子、转化点", icon: FileSearch },
  { key: "adjust", title: "调整说明", desc: "告诉 AI 你的产品/账号定位", icon: Wand2 },
  { key: "generate", title: "一键生成", desc: "输出适配你账号的复刻文案", icon: Sparkles }
]

export default function StudioCreateViralPage() {
  const searchParams = useSearchParams()
  const initPrompt = (() => {
    const stored =
      typeof window !== "undefined" ? sessionStorage.getItem("aaf:launcher:prompt") : null
    if (stored) {
      sessionStorage.removeItem("aaf:launcher:prompt")
      return stored
    }
    return searchParams.get("prompt") ?? ""
  })()

  const [activeStep, setActiveStep] = useState(0)
  const [viralContent, setViralContent] = useState(initPrompt) // 第1步：爆款原文
  const [analysis, setAnalysis] = useState("") // 第2步：AI 分析结果
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [userNotes, setUserNotes] = useState("") // 第3步：调整说明
  const [result, setResult] = useState("") // 第4步：生成结果
  const [isGenerating, setIsGenerating] = useState(false)
  const abortRef = useRef<AbortController | null>(null)

  // 步骤 2：AI 分析
  const handleAnalyze = useCallback(async () => {
    if (!viralContent.trim()) return
    setIsAnalyzing(true)
    setAnalysis("")
    abortRef.current = new AbortController()
    try {
      await postAiStream(
        "/aigc/copywriting/analyze",
        { content: viralContent.trim() },
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
  }, [viralContent])

  // 下一步
  const goNext = useCallback(async () => {
    if (activeStep === 0 && !viralContent.trim()) {
      notify.warning("请先输入爆款内容")
      return
    }
    // 进入第2步时自动触发分析
    if (activeStep === 0) {
      setActiveStep(1)
      // 延迟执行，等状态更新
      setTimeout(async () => {
        setIsAnalyzing(true)
        setAnalysis("")
        abortRef.current = new AbortController()
        try {
          await postAiStream(
            "/aigc/copywriting/analyze",
            { content: viralContent.trim() },
            {
              onChunk: (chunk) => setAnalysis((prev) => prev + chunk),
              onDone: () => setIsAnalyzing(false),
              onError: () => {
                setIsAnalyzing(false)
                notify.error("分析失败，请重试")
              },
              signal: abortRef.current?.signal
            }
          )
        } catch {
          setIsAnalyzing(false)
        }
      }, 0)
      return
    }
    if (activeStep === 1 && isAnalyzing) {
      notify.warning("AI 正在分析中，请稍候...")
      return
    }
    if (activeStep === 1 && !analysis) {
      notify.warning("请先完成 AI 分析")
      return
    }
    if (activeStep === 1) {
      setActiveStep(2)
      return
    }
    // 进入第4步时自动触发生成
    if (activeStep === 2) {
      setActiveStep(3)
      setIsGenerating(true)
      setResult("")
      abortRef.current = new AbortController()
      postAiStream(
        "/aigc/copywriting/generate",
        { topic: analysis, userNotes },
        {
          onChunk: (chunk) => setResult((prev) => prev + chunk),
          onDone: () => setIsGenerating(false),
          onError: () => {
            setIsGenerating(false)
            notify.error("生成失败，请重试")
          },
          signal: abortRef.current.signal
        }
      ).catch(() => setIsGenerating(false))
      return
    }
  }, [activeStep, viralContent, isAnalyzing, analysis, userNotes])

  const handleSave = useCallback(() => {
    if (!result) return
    navigator.clipboard.writeText(result).then(() => notify.success("文案已复制到剪贴板"))
  }, [result])

  const createDoc = useCreateDocument()
  const handleSaveDoc = useCallback(() => {
    if (!result) return
    const title = `爆款复制-${new Date().toLocaleDateString("zh-CN")}`
    createDoc.mutate(
      { title, docType: "markdown", content: result },
      {
        onSuccess: () => notify.success("已保存到文档，可在「知识-文档」中查看"),
        onError: () => notify.error("保存失败")
      }
    )
  }, [result, createDoc])

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <Zap className="size-5 text-amber-400" />
          <h1 className="font-semibold text-xl">爆款复制</h1>
          <NeonChip tone="rose" size="sm" dot>
            热门
          </NeonChip>
        </div>
        <p className="text-muted-foreground text-sm">
          输入爆款文案，AI 拆解套路，一键生成属于你的版本
        </p>
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
                  onClick={() => isDone && setActiveStep(idx)}
                  disabled={!isDone}
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

          {/* 步骤 1：输入爆款文本 */}
          {activeStep === 0 && (
            <Textarea
              value={viralContent}
              onChange={(e) => setViralContent(e.target.value)}
              placeholder="粘贴爆款文案、标题、视频文案或内容描述..."
              className="h-[260px] resize-none overflow-y-auto bg-foreground/[0.02]"
              maxLength={3000}
            />
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
              placeholder="请描述你的产品或需求，例如：我卖有机护肤品，目标用户是 25-35 岁都市女白领，希望风格轻松活泼..."
              value={userNotes}
              onChange={(e) => setUserNotes(e.target.value)}
              className="min-h-32 bg-foreground/[0.02]"
            />
          )}

          {/* 步骤 4：生成结果（可手动调整） */}
          {activeStep === 3 && (
            <div className="space-y-3">
              {isGenerating && (
                <div className="flex items-center gap-2 text-sm text-violet-400">
                  <Loader2 className="size-4 animate-spin" />
                  <span>AI 正在生成文案...</span>
                </div>
              )}
              <Textarea
                value={result}
                onChange={(e) => setResult(e.target.value)}
                placeholder="生成结果将在此显示，可手动调整..."
                className="min-h-[200px] bg-foreground/[0.02]"
              />
              {result && (
                <div className="flex items-center justify-between">
                  <GlowButton tone="ghost" size="sm" onClick={handleSave}>
                    复制到剪贴板
                  </GlowButton>
                  <GlowButton
                    tone="violet"
                    size="sm"
                    onClick={handleSaveDoc}
                    disabled={createDoc.isPending}
                  >
                    {createDoc.isPending ? "保存中..." : "保存文档"}
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
