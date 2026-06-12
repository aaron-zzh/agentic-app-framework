/**
 * 文案生成面板——从底部弹起，支持口播/小红书类型、模板库、翻译、改写、长度设置
 * 以及爆款复制三步向导（分析→调整→生成）
 * 模型列表从后端动态查询；生成结果以富文本（Markdown）渲染；底部按钮可通过 actions prop 动态配置
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { AnimatePresence, m } from "framer-motion"
import { Edit3, FileText, RefreshCw, Sparkles, X } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import ReactMarkdown from "react-markdown"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { RichTextEditor, type RichTextEditorHandle } from "@/features/rich-text-editor"
import { copywritingApi } from "@/lib/api/rest/ai"
import { listTextModels } from "@/lib/api/rest/ai/ai-model"
import { PromptTemplateDialog } from "../generation/PromptTemplateDialog"
import { RoleSelector } from "../generation/RoleSelector"
import { useAigcStore } from "../store"

const TEMPLATES = [
  { value: "", label: "无模板" },
  { value: "product-launch", label: "新品上市" },
  { value: "promotion", label: "促销活动" },
  { value: "brand-story", label: "品牌故事" },
  { value: "tutorial", label: "教程攻略" },
  { value: "review", label: "测评分享" }
]

const TRANSLATE_OPTIONS = [
  { value: "", label: "不翻译" },
  { value: "en", label: "英文" },
  { value: "ja", label: "日文" },
  { value: "ko", label: "韩文" },
  { value: "fr", label: "法文" },
  { value: "es", label: "西班牙文" }
]

/** 动作按钮配置 */
export interface CopywritingAction {
  key: string
  label: string
  icon?: React.ReactNode
  /** variant 对应 shadcn Button variant */
  variant?: "default" | "outline" | "ghost" | "destructive" | "secondary"
  className?: string
  /** 是否在 generating 时禁用（默认 true） */
  disableWhileGenerating?: boolean
  onClick: (ctx: { content: string; generating: boolean }) => void
}

interface Props {
  /** 自定义底部按钮列表，不传时使用默认的改写+生成 */
  actions?: CopywritingAction[]
}

export function CopywritingPanel({ actions }: Props) {
  const open = useAigcStore((s) => s.copywritingPanelOpen)
  const setOpen = useAigcStore((s) => s.setCopywritingPanelOpen)
  const content = useAigcStore((s) => s.copywritingContent)
  const setContent = useAigcStore((s) => s.setCopywritingContent)
  const type = useAigcStore((s) => s.copywritingType)
  const setType = useAigcStore((s) => s.setCopywritingType)
  const template = useAigcStore((s) => s.copywritingTemplate)
  const setTemplate = useAigcStore((s) => s.setCopywritingTemplate)
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((s) => s.setCopywritingTranslateTo)
  const length = useAigcStore((s) => s.copywritingLength)
  const setLength = useAigcStore((s) => s.setCopywritingLength)
  const model = useAigcStore((s) => s.copywritingModel)
  const setModel = useAigcStore((s) => s.setCopywritingModel)
  const agentRole = useAigcStore((s) => s.agentRole)
  const setAgentRole = useAigcStore((s) => s.setAgentRole)

  const [generating, setGenerating] = useState(false)
  const [showResult, setShowResult] = useState(false)

  // 爆款复制向导状态
  const [viralStep, setViralStep] = useState<1 | 2 | 3>(1)
  const [viralSource, setViralSource] = useState("")
  const [viralAnalysis, setViralAnalysis] = useState("")
  const [analyzing, setAnalyzing] = useState(false)
  const analysisEditorRef = useRef<RichTextEditorHandle>(null)
  const resultEditorRef = useRef<RichTextEditorHandle>(null)

  // 切换到非 viral 时重置向导
  useEffect(() => {
    if (type !== "viral") {
      setViralStep(1)
      setViralSource("")
      setViralAnalysis("")
    }
  }, [type])

  const { data: textModels = [] } = useQuery({
    queryKey: ["ai", "models", "text"],
    queryFn: listTextModels,
    staleTime: 5 * 60 * 1000
  })

  useEffect(() => {
    if (!model && textModels.length > 0) setModel(textModels[0].modelId)
  }, [model, textModels, setModel])

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
    const delta = resizeStart.current.my - e.clientY
    setPanelHeight(Math.max(200, resizeStart.current.h + delta))
  }, [])

  const handleResizeUp = useCallback(() => {
    resizeStart.current = null
  }, [])

  async function handleGenerate() {
    setGenerating(true)
    setShowResult(true)
    setContent("")
    let acc = ""
    await copywritingApi.generate(
      {
        topic: content || "新品发布",
        type,
        template,
        length,
        translateTo: translateTo || undefined,
        modelId: model || undefined
      },
      {
        onChunk: (chunk) => {
          acc += chunk
          setContent(acc)
        },
        onDone: () => setGenerating(false),
        onError: (err) => {
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  async function handleRewrite() {
    if (!content.trim()) return
    const original = content
    setGenerating(true)
    setShowResult(true)
    setContent("")
    let acc = ""
    await copywritingApi.rewrite(
      { content: original, modelId: model || undefined },
      {
        onChunk: (chunk) => {
          acc += chunk
          setContent(acc)
        },
        onDone: () => setGenerating(false),
        onError: (err) => {
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  async function handleAnalyze() {
    if (!viralSource.trim()) return
    setAnalyzing(true)
    setViralAnalysis("")
    setViralStep(2)
    let acc = ""
    await copywritingApi.analyze(
      { content: viralSource, modelId: model || undefined },
      {
        onChunk: (chunk) => {
          acc += chunk
          setViralAnalysis(acc)
        },
        onDone: () => {
          setViralAnalysis(acc)
          setAnalyzing(false)
        },
        onError: (err) => {
          setAnalyzing(false)
          toast.error(err?.message ?? "分析失败")
        }
      }
    )
  }

  async function handleViralGenerate() {
    setGenerating(true)
    setViralStep(3)
    setContent("")
    let acc = ""
    await copywritingApi.generate(
      {
        topic: "参考爆款结构创作",
        type: "oral",
        template,
        length,
        modelId: model || undefined,
        referenceAnalysis: viralAnalysis
      },
      {
        onChunk: (chunk) => {
          acc += chunk
          setContent(acc)
        },
        onDone: () => setGenerating(false),
        onError: (err) => {
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  const defaultActions: CopywritingAction[] = [
    {
      key: "rewrite",
      label: "改写",
      icon: <RefreshCw className="size-3" />,
      variant: "outline",
      disableWhileGenerating: true,
      onClick: () => handleRewrite()
    },
    {
      key: "generate",
      label: generating ? "生成中..." : "生成",
      icon: <FileText className="mr-1 size-3" />,
      className:
        "bg-gradient-to-r from-emerald-500 to-teal-500 text-white hover:from-emerald-600 hover:to-teal-600",
      disableWhileGenerating: true,
      onClick: () => handleGenerate()
    }
  ]

  const resolvedActions = actions ?? defaultActions

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
          {/* 拖拽手柄 + 关闭 */}
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
            {/* 类型切换 */}
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <Label className="shrink-0 text-muted-foreground text-xs">类型</Label>
                <Tabs
                  value={type}
                  onValueChange={(v) => setType(v as "oral" | "xiaohongshu" | "viral")}
                >
                  <TabsList className="h-7">
                    <TabsTrigger value="oral" className="h-6 px-3 text-xs">
                      口播
                    </TabsTrigger>
                    <TabsTrigger value="xiaohongshu" className="h-6 px-3 text-xs">
                      小红书
                    </TabsTrigger>
                    <TabsTrigger value="viral" className="h-6 px-3 text-xs">
                      <Sparkles className="mr-1 size-3" />
                      爆款复制
                    </TabsTrigger>
                  </TabsList>
                </Tabs>
              </div>
              {(type !== "viral" || viralStep === 3) && (
                <Button
                  variant="ghost"
                  size="xs"
                  className="gap-1 text-muted-foreground hover:text-foreground"
                  title="将当前内容发送到图像生成"
                  onClick={() => {
                    const text = type === "viral" ? content : content
                    useAigcStore.getState().setPrompt(text.trim())
                    useAigcStore.getState().setGenerationPanelOpen(true)
                    setOpen(false)
                  }}
                >
                  <Sparkles className="size-3" />
                  生成图像
                </Button>
              )}
            </div>

            {/* 爆款复制三步向导 */}
            {type === "viral" ? (
              <ViralWizard
                step={viralStep}
                source={viralSource}
                analysis={viralAnalysis}
                analyzing={analyzing}
                generating={generating}
                result={content}
                editorRef={analysisEditorRef}
                resultEditorRef={resultEditorRef}
                onSourceChange={setViralSource}
                onAnalysisChange={setViralAnalysis}
                onAnalyze={handleAnalyze}
                onGenerate={handleViralGenerate}
                onBack={() => setViralStep(viralStep === 3 ? 2 : 1)}
              />
            ) : (
              /* 普通模式：输入态 / 结果富文本态 */
              <div className="flex min-h-[120px] flex-1 flex-col gap-1">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Label className="text-muted-foreground text-xs">
                      {showResult ? "生成结果" : "文案内容"}
                    </Label>
                    {showResult && (
                      <button
                        type="button"
                        className="flex items-center gap-1 rounded text-muted-foreground text-xs hover:text-foreground"
                        onClick={() => setShowResult(false)}
                      >
                        <Edit3 className="size-3" />
                        编辑
                      </button>
                    )}
                  </div>
                  {!showResult && (
                    <PromptTemplateDialog type="COPYWRITING" onSelect={(p) => setContent(p)} />
                  )}
                </div>
                {showResult ? (
                  <div className="prose prose-sm dark:prose-invert flex-1 overflow-y-auto rounded-md border bg-muted/30 p-3 text-sm">
                    {content ? (
                      <ReactMarkdown>{content}</ReactMarkdown>
                    ) : (
                      <span className="text-muted-foreground text-xs italic">
                        {generating ? "生成中..." : "暂无内容"}
                      </span>
                    )}
                  </div>
                ) : (
                  <Textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    placeholder="在此输入主题或关键词，或直接粘贴需要改写的文案..."
                    className="flex-1 resize-none text-sm"
                  />
                )}
              </div>
            )}
          </div>

          {/* 底部参数栏（viral 模式隐藏动作按钮，保留模型/模板/长度等参数） */}
          <div className="shrink-0 border-t px-4 py-3">
            <div className="flex flex-wrap items-center justify-center gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Select value={model} onValueChange={(v) => setModel(v ?? "")}>
                  <SelectTrigger className="h-8 w-[160px] text-xs">
                    <span className="shrink-0 text-muted-foreground">模型</span>
                    <span className="truncate">
                      {textModels.find((m) => m.modelId === model)?.displayName ?? model}
                    </span>
                  </SelectTrigger>
                  <SelectContent>
                    {textModels.map((m) => (
                      <SelectItem key={m.modelId} value={m.modelId}>
                        {m.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Select value={template} onValueChange={(v) => setTemplate(v ?? "")}>
                  <SelectTrigger className="h-8 w-[130px] text-xs">
                    <span className="shrink-0 text-muted-foreground">模板</span>
                    <span className="truncate">
                      {TEMPLATES.find((t) => t.value === template)?.label ?? "无模板"}
                    </span>
                  </SelectTrigger>
                  <SelectContent>
                    {TEMPLATES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Select
                  value={length}
                  onValueChange={(v) => setLength(v as "short" | "medium" | "long")}
                >
                  <SelectTrigger className="h-8 w-[110px] text-xs">
                    <span className="shrink-0 text-muted-foreground">长度</span>
                    <span className="truncate">
                      {{ short: "短篇", medium: "中篇", long: "长篇" }[length] ?? length}
                    </span>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="short">短篇（≤200字）</SelectItem>
                    <SelectItem value="medium">中篇（200-500字）</SelectItem>
                    <SelectItem value="long">长篇（500+字）</SelectItem>
                  </SelectContent>
                </Select>

                <Select value={translateTo} onValueChange={(v) => setTranslateTo(v ?? "")}>
                  <SelectTrigger className="h-8 w-[110px] text-xs">
                    <span className="shrink-0 text-muted-foreground">翻译</span>
                    <span className="truncate">
                      {TRANSLATE_OPTIONS.find((o) => o.value === translateTo)?.label ?? "不翻译"}
                    </span>
                  </SelectTrigger>
                  <SelectContent>
                    {TRANSLATE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <RoleSelector value={agentRole} onChange={setAgentRole} />

              {type === "viral" ? (
                <>
                  {viralStep === 1 && (
                    <Button
                      size="sm"
                      className="h-8 gap-1 bg-gradient-to-r from-violet-500 to-indigo-500 text-white text-xs hover:from-violet-600 hover:to-indigo-600"
                      disabled={!viralSource.trim() || analyzing}
                      onClick={handleAnalyze}
                    >
                      <Sparkles className="size-3" />
                      {analyzing ? "分析中..." : "分析结构"}
                    </Button>
                  )}
                  {viralStep === 2 && (
                    <>
                      <Button
                        size="sm"
                        variant="outline"
                        className="h-8 text-xs"
                        disabled={analyzing}
                        onClick={handleAnalyze}
                      >
                        <RefreshCw className="mr-1 size-3" />
                        重新分析
                      </Button>
                      <Button
                        size="sm"
                        className="h-8 gap-1 bg-gradient-to-r from-emerald-500 to-teal-500 text-white text-xs hover:from-emerald-600 hover:to-teal-600"
                        disabled={generating || !viralAnalysis.trim()}
                        onClick={handleViralGenerate}
                      >
                        <FileText className="mr-1 size-3" />
                        {generating ? "生成中..." : "按此生成"}
                      </Button>
                    </>
                  )}
                  {viralStep === 3 && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="h-8 gap-1 text-xs"
                      disabled={generating}
                      onClick={handleViralGenerate}
                    >
                      <RefreshCw className="size-3" />
                      {generating ? "生成中..." : "重新生成"}
                    </Button>
                  )}
                </>
              ) : (
                resolvedActions.map((action, i) => {
                  const isLast = i === resolvedActions.length - 1
                  const prevIsNotLast = i > 0
                  return (
                    <div key={action.key} className="flex items-center gap-2">
                      {prevIsNotLast && isLast && (
                        <Separator orientation="vertical" className="h-5" />
                      )}
                      <Button
                        size="sm"
                        variant={action.variant ?? "default"}
                        disabled={
                          (action.disableWhileGenerating !== false && generating) ||
                          (action.key === "rewrite" && !content.trim())
                        }
                        onClick={() => action.onClick({ content, generating })}
                        className={`h-8 gap-1 text-xs ${action.className ?? ""}`}
                      >
                        {action.icon}
                        {action.key === "generate"
                          ? generating
                            ? "生成中..."
                            : "生成"
                          : action.label}
                      </Button>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </m.div>
      )}
    </AnimatePresence>
  )
}

// ——— 爆款复制三步向导子组件 ———

interface ViralWizardProps {
  step: 1 | 2 | 3
  source: string
  analysis: string
  analyzing: boolean
  generating: boolean
  result: string
  editorRef?: React.RefObject<RichTextEditorHandle | null>
  resultEditorRef?: React.RefObject<RichTextEditorHandle | null>
  onSourceChange: (v: string) => void
  onAnalysisChange: (v: string) => void
  onAnalyze: () => void
  onGenerate: () => void
  onBack: () => void
}

function ViralWizard({
  step,
  source,
  analysis,
  analyzing,
  generating,
  result,
  editorRef,
  resultEditorRef,
  onSourceChange,
  onAnalysisChange,
  onAnalyze: _onAnalyze,
  onGenerate: _onGenerate,
  onBack
}: ViralWizardProps) {
  return (
    <div className="flex flex-1 flex-col gap-3">
      {/* 步骤指示器 */}
      <div className="flex items-center gap-2 text-muted-foreground text-xs">
        {(["输入原文", "调整分析", "生成结果"] as const).map((label, i) => (
          <span key={label} className="flex items-center gap-1">
            <span
              className={`flex size-4 items-center justify-center rounded-full text-[10px] ${step === i + 1 ? "bg-primary text-primary-foreground" : step > i + 1 ? "bg-primary/30 text-primary" : "bg-muted"}`}
            >
              {i + 1}
            </span>
            <span className={step === i + 1 ? "font-medium text-foreground" : ""}>{label}</span>
            {i < 2 && <span className="mx-1">›</span>}
          </span>
        ))}
      </div>

      {/* 步骤1：输入爆款原文 */}
      {step === 1 && (
        <div className="flex flex-1 flex-col gap-2">
          <Textarea
            value={source}
            onChange={(e) => onSourceChange(e.target.value)}
            placeholder="粘贴你想借鉴的爆款文章、视频文案或帖子内容..."
            className="flex-1 resize-none text-sm"
          />
        </div>
      )}

      {/* 步骤2：分析结果可编辑 + 生成 */}
      {step === 2 && (
        <div className="flex flex-1 flex-col gap-2">
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground text-xs">
              可直接编辑分析结果，调整后点击生成
            </span>
            <button
              type="button"
              className="text-muted-foreground text-xs hover:text-foreground"
              onClick={onBack}
            >
              ← 重新输入
            </button>
          </div>
          <div className="relative flex-1 overflow-hidden rounded-md border">
            {analyzing ? (
              <div className="prose prose-sm dark:prose-invert h-full overflow-y-auto p-3 text-sm">
                <ReactMarkdown>{analysis}</ReactMarkdown>
                <div className="pointer-events-none sticky bottom-2 flex justify-center">
                  <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                </div>
              </div>
            ) : (
              <RichTextEditor
                ref={editorRef}
                value={analysis}
                onChange={onAnalysisChange}
                preset="richField"
                mode="markdown"
                fill
                className="h-full border-0 text-sm"
              />
            )}
          </div>
        </div>
      )}

      {/* 步骤3：生成结果 + 重新生成 */}
      {step === 3 && (
        <div className="flex flex-1 flex-col gap-2">
          <div className="flex items-center justify-between">
            <Label className="text-muted-foreground text-xs">生成结果</Label>
            <button
              type="button"
              className="text-muted-foreground text-xs hover:text-foreground"
              onClick={onBack}
            >
              ← 返回调整
            </button>
          </div>
          <div className="relative flex-1 overflow-hidden rounded-md border">
            {generating ? (
              <div className="prose prose-sm dark:prose-invert h-full overflow-y-auto p-3 text-sm">
                <ReactMarkdown>{result}</ReactMarkdown>
                <div className="pointer-events-none sticky bottom-2 flex justify-center">
                  <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                </div>
              </div>
            ) : (
              <RichTextEditor
                ref={resultEditorRef}
                value={result}
                preset="richField"
                mode="markdown"
                fill
                className="h-full border-0 text-sm"
              />
            )}
          </div>
        </div>
      )}
    </div>
  )
}
