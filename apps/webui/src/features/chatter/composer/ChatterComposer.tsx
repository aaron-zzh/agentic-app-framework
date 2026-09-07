/**
 * ChatterComposer——输入区
 * 按 assistant-ui 官方推荐结构：大圆角卡片，Input 在上，工具栏在下
 *
 * 底部工具栏：左 + 附件 | 模型选择 | 右 3D波形（语音时）+ 麦克风 + 发送/停止
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  AuiIf,
  ComposerPrimitive,
  unstable_useComposerInputHistory,
  useAui,
  useAuiEvent,
  useAuiState
} from "@assistant-ui/react"
import {
  ArrowUpIcon,
  BrainCircuitIcon,
  FileTextIcon,
  ImageIcon,
  ListTodoIcon,
  PaperclipIcon,
  SquareIcon
} from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { ModelSelector } from "@/components/common/ModelSelector"
import { Button } from "@/components/ui/button"
import { Toggle } from "@/components/ui/toggle"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { ContextChip } from "@/features/chatter/dnd/ContextChip"
import {
  type ChatterDisplayPreferences,
  type ChatterDropItem,
  DEFAULT_TASK_MODEL_SELECTION,
  type TaskModelSelection
} from "@/features/chatter/types"
import { VoiceWaveform3D } from "@/features/livechat/voice/VoiceWaveform3D"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { useModelSelector } from "@/lib/hooks/use-model-selector"

interface ChatterComposerProps {
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  /** 粘贴长文本时回调，由上层决定是否转为 chip */
  onPasteText?: (item: ChatterDropItem) => void
  /** 发送后清空所有 attachments */
  onAfterSend?: () => void
  taskModelSelection: TaskModelSelection
  onTaskModelSelectionChange: (selection: TaskModelSelection) => void
  /** 是否显示模型选择器，默认 true；未登录场景应传 false */
  showModelSelector?: boolean
  displayPreferences: ChatterDisplayPreferences
  onDisplayPreferencesChange: (preferences: ChatterDisplayPreferences) => void
}

/** 粘贴文本超过此长度时折叠为 chip */
const PASTE_CHIP_THRESHOLD = 200

export function ChatterComposer({
  attachments,
  onAttachmentRemove,
  onPasteText,
  onAfterSend,
  taskModelSelection,
  onTaskModelSelectionChange,
  showModelSelector = true,
  displayPreferences,
  onDisplayPreferencesChange
}: ChatterComposerProps) {
  const api = useAui()
  useAuiEvent("composer.attachmentAddError", ({ reason, message }) => {
    if (reason === "not-accepted") {
      toast.error("仅支持图片和文本文件")
      return
    }
    toast.error(message || "附件添加失败")
  })
  const [waveformCtx, setWaveformCtx] = useState<MediaStream | null>(null)
  const composerBoxRef = useRef<HTMLDivElement>(null)
  const voiceBaseTextRef = useRef("")

  // AAF-114 #11412：官方实验 API（unstable_，可能无预警变更），封装其边界——只消费返回的 onKeyDown，
  // 不额外包装逻辑。已内置处理 IME 组合、编辑态 composer、mention/slash popover 与已 preventDefault
  // 的宿主 handler，仅在空草稿且光标在首/末行时以 ArrowUp/ArrowDown 召回历史发送过的消息。
  const inputHistory = unstable_useComposerInputHistory()

  const handleVoiceResult = useCallback(
    (text: string) => {
      const base = voiceBaseTextRef.current.trimEnd()
      const voiceText = text.trim()
      api.composer().setText(base && voiceText ? `${base}\n${voiceText}` : base || voiceText)
    },
    [api]
  )

  const handleVoiceRecordingChange = useCallback(
    (stream: MediaStream | null) => {
      if (stream) {
        voiceBaseTextRef.current = api.composer().getState().text ?? ""
      }
      setWaveformCtx(stream)
    },
    [api]
  )

  // 发送前把 text chip 内容 prepend 到输入框，再调 send
  const handleSend = useCallback(() => {
    const textChips = attachments.filter((a) => a.type === "text" && a.content)
    if (textChips.length > 0) {
      const current = api.composer().getState().text ?? ""
      const prefix = textChips.map((a) => a.content).join("\n\n")
      api.composer().setText(prefix + (current ? `\n\n${current}` : ""))
    }
    api.composer().send()
    onAfterSend?.()
  }, [api, attachments, onAfterSend])

  // 在捕获阶段拦截 paste，优先于 assistant-ui 内部处理
  useEffect(() => {
    const box = composerBoxRef.current
    if (!box || !onPasteText) return
    const handler = (e: ClipboardEvent) => {
      if ((e.clipboardData?.files.length ?? 0) > 0) return
      const text = e.clipboardData?.getData("text/plain") ?? ""
      if (text.length < PASTE_CHIP_THRESHOLD) return
      const target = e.target as HTMLTextAreaElement | null
      const selStart = target?.selectionStart ?? 0
      e.preventDefault()
      e.stopPropagation()
      onPasteText({
        type: "text",
        title: "粘贴文本",
        summary: text.slice(0, 60) + (text.length > 60 ? "…" : ""),
        content: text
      })
      // 恢复焦点并保持光标原位置
      // 用 execCommand 插入空字符串使浏览器 undo 栈记录此操作，支持 Ctrl+Z
      requestAnimationFrame(() => {
        const textarea = box.querySelector("textarea")
        if (textarea) {
          textarea.focus()
          textarea.setSelectionRange(selStart, selStart)
          // biome-ignore lint/suspicious/noExplicitAny: execCommand deprecated but still works for undo support
          ;(document as any).execCommand("insertText", false, "")
        }
      })
    }
    box.addEventListener("paste", handler, true) // capture=true
    return () => box.removeEventListener("paste", handler, true)
  }, [onPasteText])

  return (
    <ComposerPrimitive.AttachmentDropzone className="data-[dragging=true]:rounded-xl data-[dragging=true]:ring-2 data-[dragging=true]:ring-primary/50">
      <ComposerPrimitive.Root className="px-3 pb-3">
        <div
          ref={composerBoxRef}
          className="rounded-xl border border-border bg-background transition-colors focus-within:border-foreground/60"
        >
          {/* 文件附件由 assistant-ui composer 作为唯一状态源管理。 */}
          <ComposerPrimitive.Attachments>
            {({ attachment }) => {
              const AttachmentIcon = attachment.type === "image" ? ImageIcon : FileTextIcon
              return (
                <div className="relative mx-2 mt-2 inline-flex max-w-[calc(100%-1rem)] items-center gap-2 rounded-lg border border-border bg-muted/40 py-1.5 pr-7 pl-2">
                  <AttachmentIcon className="size-4 shrink-0 text-muted-foreground" />
                  <span className="truncate text-xs" title={attachment.name}>
                    {attachment.name}
                  </span>
                  <button
                    type="button"
                    aria-label={`移除附件 ${attachment.name}`}
                    onClick={() => api.composer().attachment({ id: attachment.id }).remove()}
                    className="absolute right-1 flex size-5 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
                  >
                    <span className="text-sm leading-none">×</span>
                  </button>
                </div>
              )
            }}
          </ComposerPrimitive.Attachments>
          {/* 自定义 text/doc chip */}
          {attachments.length > 0 && (
            <div className="flex flex-wrap gap-1 px-3 pt-2">
              {attachments.map((item, i) => (
                <ContextChip
                  key={`${item.type}-${item.id ?? i}`}
                  item={item}
                  onRemove={() => onAttachmentRemove(i)}
                />
              ))}
            </div>
          )}

          {/* 输入框 */}
          <ComposerPrimitive.Input
            placeholder="输入消息..."
            className="field-sizing-content max-h-36 w-full resize-none bg-transparent px-3 pt-2.5 pb-2 text-sm leading-5 placeholder:text-muted-foreground focus:outline-none"
            rows={1}
            {...inputHistory}
          />

          {/* 底部工具栏 */}
          <div className="relative flex items-center justify-between px-1.5 pb-1.5">
            {/* 左：附件 + 模型 + 展示偏好 */}
            <div className="flex min-w-0 items-center gap-0.5">
              <ComposerPrimitive.AddAttachment asChild>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-7 shrink-0 rounded-lg"
                  aria-label="添加图片或文本附件"
                >
                  <PaperclipIcon className="size-4" />
                </Button>
              </ComposerPrimitive.AddAttachment>

              {showModelSelector && (
                <ModelSelectorSlot
                  taskModelSelection={taskModelSelection}
                  onTaskModelSelectionChange={onTaskModelSelectionChange}
                />
              )}

              <DisplayPreferenceToggles
                preferences={displayPreferences}
                onChange={onDisplayPreferencesChange}
              />
            </div>

            {/* 右：3D波形（语音激活时）+ 麦克风 + 发送/停止 */}
            <div className="flex items-center gap-1">
              {waveformCtx && (
                <div className="pointer-events-none absolute inset-y-1.5 right-[100px] left-[100px] overflow-hidden rounded-lg">
                  <VoiceWaveform3D stream={waveformCtx} />
                </div>
              )}

              <WsAsrButton
                onResult={handleVoiceResult}
                onInterim={handleVoiceResult}
                onRecordingChange={handleVoiceRecordingChange}
              />

              <AuiIf condition={(s) => !s.thread.isRunning}>
                <Button size="icon" className="size-7 rounded-lg" onClick={handleSend}>
                  <ArrowUpIcon className="size-4" />
                </Button>
              </AuiIf>
              <AuiIf condition={(s) => s.thread.isRunning}>
                <ComposerPrimitive.Cancel asChild>
                  <Button
                    type="button"
                    variant="secondary"
                    size="icon"
                    className="size-7 rounded-lg"
                  >
                    <SquareIcon className="size-3 fill-current" />
                  </Button>
                </ComposerPrimitive.Cancel>
              </AuiIf>
            </div>
          </div>
        </div>
      </ComposerPrimitive.Root>
    </ComposerPrimitive.AttachmentDropzone>
  )
}

function DisplayPreferenceToggles({
  preferences,
  onChange
}: {
  preferences: ChatterDisplayPreferences
  onChange: (preferences: ChatterDisplayPreferences) => void
}) {
  return (
    <TooltipProvider>
      <div className="flex shrink-0 items-center gap-0.5">
        <Tooltip>
          <TooltipTrigger
            render={
              <Toggle
                size="sm"
                pressed={preferences.showPlan}
                onPressedChange={(showPlan) => onChange({ ...preferences, showPlan })}
                aria-label="显示计划和任务进度"
                className="size-7 min-w-7 gap-0 p-0"
              />
            }
          >
            <ListTodoIcon className="size-3.5" />
            <span className="sr-only">计划</span>
          </TooltipTrigger>
          <TooltipContent>显示后端生成的计划和任务进度，不改变服务端规划决策。</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger
            render={
              <Toggle
                size="sm"
                pressed={preferences.showThinking}
                onPressedChange={(showThinking) => onChange({ ...preferences, showThinking })}
                aria-label="显示可公开的推理摘要"
                className="size-7 min-w-7 gap-0 p-0"
              />
            }
          >
            <BrainCircuitIcon className="size-3.5" />
            <span className="sr-only">思考</span>
          </TooltipTrigger>
          <TooltipContent>显示可公开的推理摘要；不会展示模型原始思维链。</TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  )
}

/**
 * 模型选择槽——仅在需要展示时渲染，避免未登录场景调用 /ai/models 触发 401
 *
 * <p>含图片附件时只展示具备 VISION 能力的模型：前端过滤仅为体验提示，不替代服务端在
 * EXPLICIT 模式下对非视觉模型的强制拒绝（AAF-114 #11406）。
 */
function ModelSelectorSlot({
  taskModelSelection,
  onTaskModelSelectionChange
}: {
  taskModelSelection: TaskModelSelection
  onTaskModelSelectionChange: (selection: TaskModelSelection) => void
}) {
  const hasImageAttachment = useAuiState((s) =>
    s.composer.attachments.some((attachment) => attachment.type === "image")
  )
  const selectedModelId = taskModelSelection.mode === "EXPLICIT" ? taskModelSelection.modelId : null
  const { options, modelId, setModelId } = useModelSelector("CHAT", {
    value: selectedModelId,
    autoSelect: false,
    onChange: (nextModelId) =>
      onTaskModelSelectionChange({ mode: "EXPLICIT", modelId: nextModelId })
  })
  const visibleOptions = hasImageAttachment
    ? options.filter((option) => option.meta.capabilities.includes("VISION"))
    : options

  return (
    <ModelSelector
      options={visibleOptions}
      value={modelId}
      onChange={setModelId}
      placeholder="任务模型"
      className="h-7 min-w-0 max-w-20 shrink gap-1 rounded-full px-1.5 text-muted-foreground text-xs hover:text-foreground"
      autoOption={{
        selected: taskModelSelection.mode === "AUTO",
        onSelect: () => onTaskModelSelectionChange(DEFAULT_TASK_MODEL_SELECTION),
        label: "自动"
      }}
    />
  )
}
