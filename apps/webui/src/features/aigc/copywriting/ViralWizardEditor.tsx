/**
 * 爆款复制模式的 body 编辑区——三步：输入原文 → 调整分析 → 生成结果
 * 与 CopywritingEditor 对称（普通模式 body ↔ 爆款模式 body），步骤动作按钮在面板 footer
 * step2/step3 复用 StreamingEditor（流式展示 + 可编辑），与普通模式同一套机制
 * @author AaronZZH & Kiro
 */

import { useEffect } from "react"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { StreamingEditor, type StreamingEditorHandle } from "@/features/rich-text-editor"

interface ViralWizardEditorProps {
  step: 1 | 2 | 3
  source: string
  /** 已提交的分析文本（step2 编辑器 value） */
  analysis: string
  /** 已提交的结果文本（step3 编辑器 value） */
  result: string
  analyzing: boolean
  generating: boolean
  analysisRef: React.RefObject<StreamingEditorHandle | null>
  resultRef: React.RefObject<StreamingEditorHandle | null>
  onSourceChange: (v: string) => void
  onAnalysisChange: (v: string) => void
  onResultChange: (v: string) => void
  onBack: () => void
}

export function ViralWizardEditor({
  step,
  source,
  analysis,
  result,
  analyzing,
  generating,
  analysisRef,
  resultRef,
  onSourceChange,
  onAnalysisChange,
  onResultChange,
  onBack
}: ViralWizardEditorProps) {
  // 切到 step2/step3 后编辑器才挂载，挂载后再触发 waiting 态（start 需在挂载后调用）
  useEffect(() => {
    if (analyzing) analysisRef.current?.start()
  }, [analyzing, analysisRef])
  useEffect(() => {
    if (generating) resultRef.current?.start()
  }, [generating, resultRef])

  return (
    <div className="flex flex-1 flex-col gap-3">
      {/* 步骤指示器 */}
      <div className="flex items-center justify-center gap-2 text-muted-foreground text-xs">
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

      {/* 步骤2：分析结果流式展示 + 可编辑 */}
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
            <StreamingEditor ref={analysisRef} value={analysis} onChange={onAnalysisChange} />
          </div>
        </div>
      )}

      {/* 步骤3：生成结果流式展示 + 可编辑 */}
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
            <StreamingEditor ref={resultRef} value={result} onChange={onResultChange} />
          </div>
        </div>
      )}
    </div>
  )
}
