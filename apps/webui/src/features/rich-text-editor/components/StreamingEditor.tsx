/**
 * StreamingEditor——单 RichTextEditor + 流式展示
 *
 * idle：RichTextEditor 可编辑
 * waiting：RichTextEditor + 外层 spinner（点击生成后等待第一个 chunk）
 * streaming：ReactMarkdown 实时渲染（收到第一个 chunk 后）
 * done：setValue 写回 RichTextEditor，可继续编辑
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useImperativeHandle, useRef, useState } from "react"
import ReactMarkdown from "react-markdown"
import type { RichTextEditorHandle } from "@/features/rich-text-editor"
import { RichTextEditor } from "@/features/rich-text-editor"
import { useStreamingText } from "@/lib/hooks/use-streaming-text"

export interface StreamingEditorHandle {
  /** 点击生成时调用，立即显示 waiting 状态 */
  start: () => void
  /** 推入 SSE chunk，自动切换到 streaming 状态 */
  push: (chunk: string) => void
  /** 流结束，写回编辑器 */
  done: (finalText: string) => void
  /** 重置为 idle */
  reset: () => void
  /** 获取当前内容 */
  getContent: (mode?: "html" | "markdown") => string
}

type Phase = "idle" | "waiting" | "streaming"

interface StreamingEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
  className?: string
  preset?: import("../presets").PresetName
  ref?: React.Ref<StreamingEditorHandle>
}

export function StreamingEditor({
  value = "",
  onChange,
  placeholder,
  className,
  preset = "richField",
  ref
}: StreamingEditorProps) {
  const [phase, setPhase] = useState<Phase>("idle")
  const phaseRef = useRef<Phase>("idle")
  const setPhaseSync = (p: Phase) => {
    phaseRef.current = p
    setPhase(p)
  }
  const editorRef = useRef<RichTextEditorHandle>(null)
  const {
    text: streamText,
    push: pushToQueue,
    start: startQueue,
    reset: resetQueue
  } = useStreamingText()

  useImperativeHandle(ref, () => ({
    start: () => {
      resetQueue()
      setPhaseSync("waiting")
    },
    push: (chunk: string) => {
      if (phaseRef.current !== "streaming") {
        startQueue()
        setPhaseSync("streaming")
      }
      pushToQueue(chunk)
    },
    done: (text: string) => {
      setPhaseSync("idle")
      resetQueue()
      editorRef.current?.setValue(text, "markdown")
    },
    reset: () => {
      resetQueue()
      setPhaseSync("idle")
    },
    getContent: (mode = "markdown") => editorRef.current?.getContent(mode) ?? value
  }))

  return (
    <div className={`relative ${className ?? "h-full"}`}>
      {/* 始终挂载编辑器，streaming 时隐藏 */}
      <div className={phase === "streaming" ? "invisible h-0 overflow-hidden" : "h-full"}>
        <RichTextEditor
          ref={editorRef}
          value={value}
          onChange={onChange}
          preset={preset}
          mode="markdown"
          initialValueMode="markdown"
          placeholder={placeholder}
          fill
          noBorder
          className="h-full text-sm"
        />
      </div>

      {/* waiting：spinner 显示在编辑器上层 */}
      {phase === "waiting" && (
        <div className="absolute inset-0 flex items-center justify-center bg-background/80">
          <div className="size-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      )}

      {/* streaming：ReactMarkdown 实时渲染 */}
      {phase === "streaming" && (
        <div className="prose prose-sm dark:prose-invert h-full overflow-y-auto p-3 text-sm">
          <ReactMarkdown>{streamText}</ReactMarkdown>
        </div>
      )}
    </div>
  )
}
