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
  groupPartByType,
  MessagePrimitive,
  type SourceMessagePart,
  ThreadPrimitive,
  useAuiState,
  useMessage,
  useVoiceState
} from "@assistant-ui/react"
import {
  ArrowDownIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  CopyIcon,
  FileTextIcon,
  ImageIcon,
  InfoIcon,
  LinkIcon,
  PencilIcon,
  Play,
  RefreshCwIcon,
  ThumbsDownIcon,
  ThumbsUpIcon,
  XIcon
} from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Textarea } from "@/components/ui/textarea"
import { deriveVoiceOrbState, VoiceControl, VoiceOrb } from "@/components/voice"
import { type AgentSuggestion, useAgentRunStore } from "@/features/livechat/runtime/agent-run-store"
import { SpeechOutput } from "@/features/livechat/voice/SpeechOutput"
import { chatApi } from "@/lib/api/rest/ai"
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

/**
 * 工具调用通用 Fallback——未注册自定义 UI 时的确定性展示（AAF-114 #11409）。
 *
 * 区分 running/error/complete 三态；error 态只展示工具名与固定提示文案，
 * 不回显 {@code status.error}/{@code result} 等字段，避免向前端泄露内部诊断信息。
 */
function ToolFallback({
  toolName,
  status,
  isError
}: {
  toolName: string
  status?: { type: string }
  isError?: boolean
}) {
  const isRunning = status?.type === "running"
  const isFailed = isError === true || status?.type === "incomplete"
  return (
    <div
      className={cn(
        "my-1.5 flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs",
        isFailed
          ? "border-destructive/30 bg-destructive/10 text-destructive"
          : "border-border/60 bg-muted/30 text-muted-foreground",
        isRunning && "animate-pulse"
      )}
    >
      <span className="flex-1 truncate">
        {isRunning
          ? `正在执行 ${toolName}...`
          : isFailed
            ? `${toolName} 执行失败`
            : `${toolName} 已完成`}
      </span>
    </div>
  )
}

/**
 * 来源导航——渲染 assistant-ui 原生 SourceMessagePart（AAF-114 #11409）。
 *
 * 直接消费 assistant-ui 官方 part 字段，不自造通用 data 卡片协议；
 * document 类型来源不含可跳转 URL，仅展示标题。
 */
function SourceLink({ part }: { part: SourceMessagePart }) {
  const title = part.title ?? (part.sourceType === "url" ? part.url : "参考文档")
  if (part.sourceType === "url") {
    return (
      <a
        href={part.url}
        target="_blank"
        rel="noopener noreferrer"
        className="my-1 flex items-center gap-1.5 rounded-md border border-border/60 bg-muted/20 px-2 py-1 text-muted-foreground text-xs hover:bg-muted/40"
      >
        <LinkIcon className="size-3 shrink-0" />
        <span className="truncate">{title}</span>
      </a>
    )
  }
  return (
    <div className="my-1 flex items-center gap-1.5 rounded-md border border-border/60 bg-muted/20 px-2 py-1 text-muted-foreground text-xs">
      <FileTextIcon className="size-3 shrink-0" />
      <span className="truncate">{title}</span>
    </div>
  )
}

/** 推理/思维链折叠块（支持流式 streaming 状态） */
function ReasoningBlock({
  streaming,
  children
}: {
  streaming?: boolean
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(false)
  return (
    <div className="my-2 rounded-lg border">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-muted-foreground text-xs hover:bg-muted/50"
        onClick={() => setOpen((o) => !o)}
      >
        {open ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        <span className={cn("flex-1 text-left", streaming && "animate-pulse")}>
          {streaming ? "思考中..." : "思考过程"}
        </span>
      </button>
      {open && <div className="px-3 pb-2">{children}</div>}
    </div>
  )
}

/** 工具调用分组折叠块 */
function ToolGroupBlock({
  count,
  active,
  children
}: {
  count: number
  active: boolean
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(active)
  // 运行时自动展开，完成后收起
  useEffect(() => {
    if (!active) setOpen(false)
  }, [active])
  return (
    <div className="my-2 rounded-lg border">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-muted-foreground text-xs hover:bg-muted/50"
        onClick={() => setOpen((o) => !o)}
      >
        {open ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        <span className={cn("flex-1 text-left", active && "animate-pulse")}>
          {active ? `正在调用 ${count} 个工具...` : `${count} 个工具调用`}
        </span>
      </button>
      {open && <div className="px-2 pb-2">{children}</div>}
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

/** 负反馈原因弹出输入框：点击后展示补充原因 Popover，提交后调用反馈接口（AAF-114 #11412） */
function NegativeFeedbackButton() {
  const message = useMessage()
  const currentThreadId = useAgentRunStore((s) => s.currentThreadId)
  const diagnostic = useAgentRunStore((s) => s.diagnostic)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState("")
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = useCallback(() => {
    if (!currentThreadId) return
    chatApi
      .submitMessageFeedback(currentThreadId, message.id, {
        type: "negative",
        reason: reason.trim() || undefined,
        model: diagnostic.modelId,
        runId: diagnostic.runId
      })
      .then(() => {
        setSubmitted(true)
        setOpen(false)
      })
      .catch(() => toast.error("反馈提交失败，请重试"))
  }, [currentThreadId, message.id, reason, diagnostic.modelId, diagnostic.runId])

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
        aria-label="点踩并说明原因"
      >
        {submitted ? (
          <ThumbsDownIcon className="size-3.5 fill-current" />
        ) : (
          <ThumbsDownIcon className="size-3.5" />
        )}
      </PopoverTrigger>
      <PopoverContent className="w-64 space-y-2" align="start">
        <p className="text-muted-foreground text-xs">这个回答有什么问题？（可选）</p>
        <Textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="告诉我们具体问题，帮助改进"
          rows={3}
          className="text-sm"
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={() => setOpen(false)}>
            取消
          </Button>
          <Button type="button" size="sm" onClick={handleSubmit}>
            提交
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

/**
 * 诊断信息按钮：受控展示 model/token/耗时/runId（AAF-114 #11412）。
 *
 * 默认不展开，点击才展示；数据源已在服务端脱敏（ExecutionEventPublicMapper 白名单），不含 provider 凭据、
 * 原始 API 响应或内部路由细节。diagnostic 是 run 级全局状态（非按消息持久化的历史数据），只在本次 run 产出的
 * 最后一条消息下展示，避免历史消息误显示成最新一次 run 的数据。
 */
function DiagnosticInfoButton() {
  const diagnostic = useAgentRunStore((s) => s.diagnostic)
  const isLast = useAuiState((s) => s.message.isLast)
  if (!isLast || (!diagnostic.modelId && diagnostic.inputTokens === undefined)) return null

  return (
    <Popover>
      <PopoverTrigger
        className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
        aria-label="查看诊断信息"
      >
        <InfoIcon className="size-3.5" />
      </PopoverTrigger>
      <PopoverContent className="w-56 space-y-1 text-xs" align="start">
        {diagnostic.modelId && (
          <div className="flex justify-between gap-2">
            <span className="text-muted-foreground">模型</span>
            <span className="truncate font-mono">{diagnostic.modelId}</span>
          </div>
        )}
        {(diagnostic.inputTokens !== undefined || diagnostic.outputTokens !== undefined) && (
          <div className="flex justify-between gap-2">
            <span className="text-muted-foreground">Token</span>
            <span className="font-mono">
              {diagnostic.inputTokens ?? 0} / {diagnostic.outputTokens ?? 0}
            </span>
          </div>
        )}
        {diagnostic.cachedTokens !== undefined && diagnostic.cachedTokens > 0 && (
          <div className="flex justify-between gap-2">
            <span className="text-muted-foreground">缓存 Token</span>
            <span className="font-mono">{diagnostic.cachedTokens}</span>
          </div>
        )}
        {diagnostic.durationSeconds !== undefined && (
          <div className="flex justify-between gap-2">
            <span className="text-muted-foreground">耗时</span>
            <span className="font-mono">{diagnostic.durationSeconds.toFixed(2)}s</span>
          </div>
        )}
        {diagnostic.runId && (
          <div className="flex justify-between gap-2">
            <span className="text-muted-foreground">Run ID</span>
            <span className="truncate font-mono">{diagnostic.runId}</span>
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}

/** AI 消息操作栏：复制 + 重新生成 + 反馈（AAF-114 #11412） */
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
      <ActionBarPrimitive.FeedbackPositive asChild>
        <button
          type="button"
          className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
          aria-label="点赞"
        >
          <AuiIf condition={(s) => s.message.metadata.submittedFeedback?.type === "positive"}>
            <ThumbsUpIcon className="size-3.5 fill-current" />
          </AuiIf>
          <AuiIf condition={(s) => s.message.metadata.submittedFeedback?.type !== "positive"}>
            <ThumbsUpIcon className="size-3.5" />
          </AuiIf>
        </button>
      </ActionBarPrimitive.FeedbackPositive>
      <NegativeFeedbackButton />
      <DiagnosticInfoButton />
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
function AssistantMessage({ showThinking }: { showThinking: boolean }) {
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
        {/* 消息内容：Markdown + 推理（Chain of Thought 分组折叠） + 工具调用 + Generative UI */}
        <MessagePrimitive.GroupedParts
          groupBy={groupPartByType({
            reasoning: ["group-chainOfThought", "group-reasoning"],
            "tool-call": ["group-chainOfThought", "group-tool"]
          })}
        >
          {({ part, children }) => {
            switch (part.type) {
              case "group-chainOfThought":
                return <div className="my-1">{children}</div>
              case "group-reasoning": {
                if (!showThinking) return null
                const streaming = part.status.type === "running"
                return <ReasoningBlock streaming={streaming}>{children}</ReasoningBlock>
              }
              case "group-tool":
                return (
                  <ToolGroupBlock
                    count={part.indices.length}
                    active={part.status.type === "running"}
                  >
                    {children}
                  </ToolGroupBlock>
                )
              case "text":
                return <MarkdownText />
              case "reasoning":
                return showThinking ? (
                  <span className="whitespace-pre-wrap text-muted-foreground text-xs italic">
                    {part.text}
                  </span>
                ) : null
              case "tool-call":
                return (
                  part.toolUI ?? (
                    <ToolFallback
                      toolName={part.toolName}
                      status={part.status}
                      isError={part.isError}
                    />
                  )
                )
              case "source":
                return <SourceLink part={part} />
              case "generative-ui":
                return <MessagePrimitive.GenerativeUI components={{}} />
              default:
                return null
            }
          }}
        </MessagePrimitive.GroupedParts>

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
      <div className="mb-1 flex max-w-[85%] flex-wrap justify-end gap-1 empty:hidden">
        <MessagePrimitive.Attachments>
          {({ attachment }) => {
            const AttachmentIcon = attachment.type === "image" ? ImageIcon : FileTextIcon
            return (
              <div className="flex max-w-48 items-center gap-1.5 rounded-lg border bg-muted/40 px-2 py-1.5 text-xs">
                <AttachmentIcon className="size-3.5 shrink-0 text-muted-foreground" />
                <span className="truncate">{attachment.name}</span>
              </div>
            )
          }}
        </MessagePrimitive.Attachments>
      </div>
      {/* 普通展示态 */}
      <AuiIf condition={(s) => !s.composer.isEditing}>
        <div className="group relative max-w-[85%]">
          <div className="rounded-2xl bg-foreground/10 px-3 py-2 text-sm dark:bg-foreground/15">
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

/** 单条建议渲染（AAF-114 #11412）：优先展示 title/description，无则回退 label/prompt；autoSend 缺省 true。 */
function SuggestionItem({ suggestion }: { suggestion: AgentSuggestion }) {
  const heading = suggestion.title ?? suggestion.label ?? suggestion.prompt
  return (
    <ThreadPrimitive.Suggestion
      prompt={suggestion.prompt}
      autoSend={suggestion.autoSend ?? true}
      className="flex w-fit flex-col gap-0.5 rounded-2xl border px-4 py-2 text-left text-sm hover:bg-muted"
    >
      <span className="font-medium">{heading}</span>
      {suggestion.description && (
        <span className="text-muted-foreground text-xs">{suggestion.description}</span>
      )}
    </ThreadPrimitive.Suggestion>
  )
}

/** 欢迎页——对话为空时显示 */
function WelcomeScreen() {
  const [suggestions, setSuggestions] = useState<AgentSuggestion[]>(DEFAULT_SUGGESTIONS)
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
          <SuggestionItem key={s.prompt} suggestion={s} />
        ))}
      </div>
    </div>
  )
}

/**
 * 对话后续建议（AAF-114 #11412）：非空对话中展示服务端推送的建议，与欢迎页共用同一 agent-run-store
 * suggestions 状态源，不建立第二套建议状态。列表本身不因用户点击而清除——服务端下一次推送新建议前，
 * 已展示的建议持续可点击（恢复语义：用户清空 composer 后仍能看到并重新选择同一批建议）。
 */
function FollowUpSuggestions() {
  const suggestions = useAgentRunStore((s) => s.suggestions)
  const phase = useAgentRunStore((s) => s.phase)
  if (suggestions.length === 0 || phase === "running") return null

  return (
    <div className="flex flex-col gap-2 px-1 py-2">
      {suggestions.map((s) => (
        <SuggestionItem key={s.prompt} suggestion={s} />
      ))}
    </div>
  )
}

export function ChatterThread({ showThinking }: { showThinking: boolean }) {
  return (
    <ThreadPrimitive.Root className="flex min-h-0 flex-1 flex-col">
      {/* 通话中显示语音控制区——VoiceSection 在 ThreadPrimitive.Root 内读 thread，合法 */}
      <VoiceSection />
      <ThreadPrimitive.Viewport className="relative min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Empty>
          <WelcomeScreen />
        </ThreadPrimitive.Empty>

        <ThreadPrimitive.Messages>
          {({ message }) =>
            message.role === "assistant" ? (
              <AssistantMessage showThinking={showThinking} />
            ) : (
              <UserMessage />
            )
          }
        </ThreadPrimitive.Messages>

        {/* 对话后续建议（AAF-114 #11412）：非空对话场景下展示服务端推送的建议，与欢迎页建议共用同一状态源 */}
        <FollowUpSuggestions />

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

/** 语音控制区——从 threads.main.voice 读取，不依赖 thread scope 的初始化时序 */
function VoiceSection() {
  const voice = useAuiState((s) => s.threads.main.voice)
  const isActive = voice != null && voice.status.type !== "ended"
  if (!isActive) return null
  return (
    <div className="flex flex-col items-center gap-2 border-b py-4">
      <ActiveVoiceOrb />
      <VoiceControl className="border-none py-0" />
    </div>
  )
}

/** 读取 voice 状态并传给 VoiceOrb（在 ThreadPrimitive.Root 内调用，context 合法） */
function ActiveVoiceOrb() {
  const voiceState = useVoiceState()
  const state = deriveVoiceOrbState(voiceState)
  return <VoiceOrb state={state} className="size-20" />
}
