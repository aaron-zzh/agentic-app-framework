/**
 * 文案生成面板——从底部弹起，支持口播/小红书类型、模板库、翻译、改写、长度设置
 * 以及爆款复制三步向导（分析→调整→生成）
 * 生成逻辑见 use-copywriting；参数栏见 CopywritingParamsBar；向导见 ViralWizardEditor
 * 底部按钮可通过 actions prop 动态配置
 * @author AaronZZH & Kiro
 */

"use client"

import { AnimatePresence, m } from "framer-motion"
import { FileText, RefreshCw, Sparkles, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useAigcStore } from "../store"
import { CopywritingEditor } from "./CopywritingEditor"
import { CopywritingHeader } from "./CopywritingHeader"
import { CopywritingParamsBar } from "./CopywritingParamsBar"
import { CopywritingReferenceImages } from "./CopywritingReferenceImages"
import type { CopywritingAction } from "./types"
import { useCopywriting } from "./use-copywriting"
import { usePanelResize } from "./use-panel-resize"
import { ViralWizardEditor } from "./ViralWizardEditor"

interface Props {
  /** 自定义底部按钮列表，不传时使用默认的改写+生成 */
  actions?: CopywritingAction[]
  /** 关联的 AIGC 项目 ID，传入时保存文档会自动关联到该项目 */
  projectId?: number
}

export function CopywritingPanel({ actions, projectId }: Props) {
  const open = useAigcStore((s) => s.copywritingPanelOpen)
  const setOpen = useAigcStore((s) => s.setCopywritingPanelOpen)
  const type = useAigcStore((s) => s.copywritingType)

  const {
    content,
    setContent,
    generating,
    saved,
    setSaved,
    streamingEditorRef,
    viralStep,
    setViralStep,
    viralSource,
    setViralSource,
    viralAnalysis,
    setViralAnalysis,
    analyzing,
    analysisEditorRef,
    resultEditorRef,
    createDoc,
    linkDoc,
    handleSaveDoc,
    handleGenerate,
    handleRewrite,
    handleAnalyze,
    handleViralGenerate
  } = useCopywriting(projectId)

  const { panelHeight, handleResizeDown, handleResizeMove, handleResizeUp } = usePanelResize()

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
              onClick={() => {
                setSaved(false)
                setOpen(false)
              }}
              aria-label="关闭"
            >
              <X className="size-4" />
            </Button>
          </div>

          {/* 编辑器 */}
          <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-4 pt-2">
            <CopywritingHeader
              showDocActions={type !== "viral" || viralStep === 3}
              saved={saved}
              savePending={createDoc.isPending || linkDoc.isPending}
              onSaveDoc={handleSaveDoc}
            />

            {/* 爆款复制三步向导 */}
            {type === "viral" ? (
              <ViralWizardEditor
                step={viralStep}
                source={viralSource}
                analysis={viralAnalysis}
                result={content}
                analyzing={analyzing}
                generating={generating}
                analysisRef={analysisEditorRef}
                resultRef={resultEditorRef}
                onSourceChange={setViralSource}
                onAnalysisChange={setViralAnalysis}
                onResultChange={setContent}
                onBack={() => setViralStep(viralStep === 3 ? 2 : 1)}
              />
            ) : (
              <>
                <CopywritingReferenceImages />
                <CopywritingEditor
                  value={content}
                  onChange={setContent}
                  editorRef={streamingEditorRef}
                />
              </>
            )}
          </div>

          {/* 底部参数栏（viral 模式隐藏动作按钮，保留模型/模板/长度等参数） */}
          <div className="shrink-0 border-t px-4 py-3">
            <div className="flex flex-wrap items-center justify-center gap-2">
              <CopywritingParamsBar />

              {type === "viral" ? (
                <ViralActions
                  step={viralStep}
                  source={viralSource}
                  analysis={viralAnalysis}
                  analyzing={analyzing}
                  generating={generating}
                  onAnalyze={handleAnalyze}
                  onGenerate={handleViralGenerate}
                />
              ) : (
                <NormalActions
                  actions={resolvedActions}
                  generating={generating}
                  content={content}
                />
              )}
            </div>
          </div>
        </m.div>
      )}
    </AnimatePresence>
  )
}

// ——— footer 动作组：爆款模式按步骤展示 ———

interface ViralActionsProps {
  step: 1 | 2 | 3
  source: string
  analysis: string
  analyzing: boolean
  generating: boolean
  onAnalyze: () => void
  onGenerate: () => void
}

function ViralActions({
  step,
  source,
  analysis,
  analyzing,
  generating,
  onAnalyze,
  onGenerate
}: ViralActionsProps) {
  if (step === 1) {
    return (
      <Button
        size="sm"
        className="h-8 gap-1 bg-linear-to-r from-violet-500 to-indigo-500 text-white text-xs hover:from-violet-600 hover:to-indigo-600"
        disabled={!source.trim() || analyzing}
        onClick={onAnalyze}
      >
        <Sparkles className="size-3" />
        {analyzing ? "分析中..." : "分析结构"}
      </Button>
    )
  }
  if (step === 2) {
    return (
      <>
        <Button
          size="sm"
          variant="outline"
          className="h-8 text-xs"
          disabled={analyzing}
          onClick={onAnalyze}
        >
          <RefreshCw className="mr-1 size-3" />
          重新分析
        </Button>
        <Button
          size="sm"
          className="h-8 gap-1 bg-linear-to-r from-emerald-500 to-teal-500 text-white text-xs hover:from-emerald-600 hover:to-teal-600"
          disabled={generating || !analysis.trim()}
          onClick={onGenerate}
        >
          <FileText className="mr-1 size-3" />
          {generating ? "生成中..." : "按此生成"}
        </Button>
      </>
    )
  }
  return (
    <Button
      size="sm"
      variant="outline"
      className="h-8 gap-1 text-xs"
      disabled={generating}
      onClick={onGenerate}
    >
      <RefreshCw className="size-3" />
      {generating ? "生成中..." : "重新生成"}
    </Button>
  )
}

// ——— footer 动作组：普通模式（改写 / 生成，可由 actions prop 自定义） ———

interface NormalActionsProps {
  actions: CopywritingAction[]
  generating: boolean
  content: string
}

function NormalActions({ actions, generating, content }: NormalActionsProps) {
  return (
    <>
      {actions.map((action, i) => {
        const isLast = i === actions.length - 1
        const prevIsNotLast = i > 0
        return (
          <div key={action.key} className="flex items-center gap-2">
            {prevIsNotLast && isLast && <Separator orientation="vertical" className="h-5" />}
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
              {action.key === "generate" ? (generating ? "生成中..." : "生成") : action.label}
            </Button>
          </div>
        )
      })}
    </>
  )
}
