/**
 * ChatterThread——消息列表区域
 *
 * 功能：
 * - Markdown 渲染（MarkdownText）
 * - 思维链/推理折叠（Reasoning）
 * - 工具调用分组展示 + Fallback（ToolGroup + ToolFallback）
 * - 错误展示（ErrorPrimitive）
 * - 分支切换（BranchPickerPrimitive）
 * - 消息操作栏（ActionBarPrimitive：复制 + 重新生成）
 * - 空状态欢迎页 + 建议提问（Suggestions）
 * - 滚动到底部按钮（ThreadPrimitive.ScrollToBottom）
 * - 用户消息编辑（ComposerPrimitive）
 * - Generative UI（MessagePrimitive.GenerativeUI）
 * - TTS 语音播放
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ActionBarPrimitive,
  AuiIf,
  BranchPickerPrimitive,
  ComposerPrimitive,
  ErrorPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useMessage
} from "@assistant-ui/react"
import {
  ArrowDownIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  CopyIcon,
  PencilIcon,
  Play,
  RefreshCwIcon,
  XIcon
} from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAgentRunStore } from "@/features/livechat/runtime/agent-run-store"
import { SpeechOutput } from "@/features/livechat/voice/SpeechOutput"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useAuthStore } from "@/lib/store/auth-store"
import { serverTtsStream, useVoiceConfig } from "@/lib/store/voice-config"
import { cn } from "@/lib/utils/cn"
import { MarkdownText } from "./MarkdownText"

/** AI 消息内容提取（用于 TTS） */
function useMessageText(): string {
  const message = useMessage()
  if (message.role !== "assistant") return ""
  return message.content
    .filter((p) => p.type === "text")
    .map((p) => ("text" in p ? p.text : ""))
    .join("")
}

/** 工具调用 Fallback——无自定义 UI 时显示通用卡片 */
function ToolFallback({ toolName, status }: { toolName: string; status?: { type: string } }) {
  const isRunning = status?.type === "running"
  return (
    <div
      className={cn(
        "my-1.5 flex items-center gap-2 rounded-lg border border-border/60 bg-muted/30 px-2.5 py-1.5 text-muted-foreground text-xs",
        isRunning && "animate-pulse"
      )}
    >
      <span className="flex-1 truncate">
        {isRunning ? `正在执行 ${toolName}...` : `${toolName} 已完成`}
      </span>
    </div>
  )
}

/** 推理/思维链折叠块 */
function ReasoningBlock({ text }: { text: string }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="my-2 rounded-lg border">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-muted-foreground text-xs hover:bg-muted/50"
        onClick={() => setOpen((o) => !o)}
      >
        {open ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        思考过程
      </button>
      {open && (
        <p className="whitespace-pre-wrap px-3 pb-2 text-muted-foreground text-xs italic">{text}</p>
      )}
    </div>
  )
}

/** 分支切换器 */
function BranchPicker() {
  return (
    <BranchPickerPrimitive.Root
      hideWhenSingleBranch
      className="inline-flex items-center gap-0.5 text-muted-foreground text-xs"
    >
      <BranchPickerPrimitive.Previous asChild>
        <button type="button" className="rounded p-0.5 hover:bg-muted">
          <ChevronLeftIcon className="size-3" />
        </button>
      </BranchPickerPrimitive.Previous>
      <span className="font-medium tabular-nums">
        <BranchPickerPrimitive.Number /> / <BranchPickerPrimitive.Count />
      </span>
      <BranchPickerPrimitive.Next asChild>
        <button type="button" className="rounded p-0.5 hover:bg-muted">
          <ChevronRightIcon className="size-3" />
        </button>
      </BranchPickerPrimitive.Next>
    </BranchPickerPrimitive.Root>
  )
}

/** AI 消息操作栏：复制 + 重新生成 */
function AssistantActionBar() {
  return (
    <ActionBarPrimitive.Root
      hideWhenRunning
      autohide="not-last"
      className="mt-1 flex items-center gap-1"
    >
      <BranchPicker />
      <ActionBarPrimitive.Copy asChild>
        <button
          type="button"
          className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <AuiIf condition={(s) => s.message.isCopied}>
            <CheckIcon className="size-3.5" />
          </AuiIf>
          <AuiIf condition={(s) => !s.message.isCopied}>
            <CopyIcon className="size-3.5" />
          </AuiIf>
        </button>
      </ActionBarPrimitive.Copy>
      <ActionBarPrimitive.Reload asChild>
        <button
          type="button"
          className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <RefreshCwIcon className="size-3.5" />
        </button>
      </ActionBarPrimitive.Reload>
    </ActionBarPrimitive.Root>
  )
}

/** 用户消息编辑表单 */
function UserEditComposer() {
  return (
    <ComposerPrimitive.Root className="w-full">
      <ComposerPrimitive.Input
        className="w-full resize-none rounded-lg border bg-background px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring"
        rows={2}
      />
      <div className="mt-2 flex justify-end gap-2">
        <ComposerPrimitive.Cancel asChild>
          <Button type="button" variant="ghost" size="sm">
            <XIcon className="mr-1 size-3.5" />
            取消
          </Button>
        </ComposerPrimitive.Cancel>
        <ComposerPrimitive.Send asChild>
          <Button size="sm">发送</Button>
        </ComposerPrimitive.Send>
      </div>
    </ComposerPrimitive.Root>
  )
}

/** AI 消息气泡 */
function AssistantMessage() {
  const text = useMessageText()
  const ttsMode = useVoiceConfig((s) => s.ttsMode)
  const ttsVoice = useVoiceConfig((s) => s.ttsVoice)

  const handleServerPlay = useCallback(async () => {
    if (!text) return
    const audioCtx = new AudioContext()
    await serverTtsStream(text, ttsVoice, async (chunk) => {
      const buffer = await audioCtx.decodeAudioData(chunk)
      const source = audioCtx.createBufferSource()
      source.buffer = buffer
      source.connect(audioCtx.destination)
      source.start()
    })
  }, [text, ttsVoice])

  return (
    <MessagePrimitive.Root className="mb-3 flex flex-col items-start">
      <div className="max-w-[85%] rounded-lg bg-muted px-3 py-2 text-sm">
        {/* 消息内容：Markdown + 推理 + 工具调用 + Generative UI */}
        <MessagePrimitive.Parts>
          {({ part }) => {
            if (part.type === "text") return <MarkdownText />
            if (part.type === "reasoning") return <ReasoningBlock text={part.text} />
            if (part.type === "tool-call")
              return part.toolUI ?? <ToolFallback toolName={part.toolName} status={part.status} />
            if (part.type === "generative-ui")
              return <MessagePrimitive.GenerativeUI components={{}} />
            return null
          }}
        </MessagePrimitive.Parts>

        {/* 错误展示 */}
        <MessagePrimitive.Error>
          <ErrorPrimitive.Root className="mt-2 rounded-md border border-destructive bg-destructive/10 p-2 text-destructive text-xs">
            <ErrorPrimitive.Message className="line-clamp-2" />
          </ErrorPrimitive.Root>
        </MessagePrimitive.Error>

        {/* TTS */}
        {text && (
          <div className="mt-1 border-t pt-1">
            {ttsMode === "browser" ? (
              <SpeechOutput text={text} />
            ) : (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={handleServerPlay}
                aria-label="播放语音"
              >
                <Play className="size-4" />
              </Button>
            )}
          </div>
        )}
      </div>

      {/* 操作栏：分支切换 + 复制 + 重新生成 */}
      <AssistantActionBar />
    </MessagePrimitive.Root>
  )
}

/** 用户消息气泡（支持编辑） */
function UserMessage() {
  return (
    <MessagePrimitive.Root className="mb-3 flex flex-col items-end">
      {/* 普通展示态 */}
      <AuiIf condition={(s) => !s.composer.isEditing}>
        <div className="group relative max-w-[85%]">
          <div className="rounded-2xl bg-primary px-3 py-2 text-primary-foreground text-sm">
            <MessagePrimitive.Parts>
              {({ part }) => (part.type === "text" ? <span>{part.text}</span> : null)}
            </MessagePrimitive.Parts>
          </div>
          {/* 编辑按钮（hover 显示） */}
          <ActionBarPrimitive.Root className="absolute top-1 -left-8 hidden group-hover:flex">
            <ActionBarPrimitive.Edit asChild>
              <button
                type="button"
                className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <PencilIcon className="size-3.5" />
              </button>
            </ActionBarPrimitive.Edit>
          </ActionBarPrimitive.Root>
        </div>
        {/* 用户消息分支切换 */}
        <div className="mt-0.5 px-1">
          <BranchPicker />
        </div>
      </AuiIf>

      {/* 编辑态 */}
      <AuiIf condition={(s) => s.composer.isEditing}>
        <div className="w-full max-w-[85%]">
          <UserEditComposer />
        </div>
      </AuiIf>
    </MessagePrimitive.Root>
  )
}

const DEFAULT_SUGGESTIONS = [
  { prompt: "你能做什么？" },
  { prompt: "帮我写一份报告" },
  { prompt: "如何使用知识库？" }
]

/** 欢迎页——对话为空时显示 */
function WelcomeScreen() {
  const [suggestions, setSuggestions] = useState(DEFAULT_SUGGESTIONS)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const dynamicSuggestions = useAgentRunStore((s) => s.suggestions)
  const displayed = dynamicSuggestions.length > 0 ? dynamicSuggestions : suggestions

  useEffect(() => {
    if (!isAuthenticated) return
    chatApi
      .getSuggestions()
      .then((res) => {
        if (res?.length > 0) setSuggestions(res)
      })
      .catch(() => {})
  }, [isAuthenticated])

  return (
    <div className="flex flex-1 flex-col justify-center gap-3 px-4 pt-16">
      <p className="text-muted-foreground text-sm">有什么可以帮你？</p>
      <div className="flex flex-col gap-2">
        {displayed.map((s) => (
          <ThreadPrimitive.Suggestion
            key={s.prompt}
            prompt={s.prompt}
            autoSend
            className="w-fit cursor-pointer rounded-full border px-4 py-1.5 text-sm hover:bg-muted"
          >
            {(s as { prompt: string; label?: string }).label ?? s.prompt}
          </ThreadPrimitive.Suggestion>
        ))}
      </div>
    </div>
  )
}

export function ChatterThread() {
  return (
    <ThreadPrimitive.Root className="flex min-h-0 flex-1 flex-col">
      <ThreadPrimitive.Viewport className="relative min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Empty>
          <WelcomeScreen />
        </ThreadPrimitive.Empty>

        <ThreadPrimitive.Messages>
          {({ message }) => (message.role === "assistant" ? <AssistantMessage /> : <UserMessage />)}
        </ThreadPrimitive.Messages>

        {/* 滚动到底部按钮 */}
        <ThreadPrimitive.ViewportFooter className="sticky bottom-0 flex justify-center pb-2">
          <ThreadPrimitive.ScrollToBottom asChild>
            <button
              type="button"
              className="rounded-full border bg-background p-1.5 shadow-sm hover:bg-muted disabled:invisible"
              aria-label="滚动到底部"
            >
              <ArrowDownIcon className="size-4" />
            </button>
          </ThreadPrimitive.ScrollToBottom>
        </ThreadPrimitive.ViewportFooter>
      </ThreadPrimitive.Viewport>
    </ThreadPrimitive.Root>
  )
}
