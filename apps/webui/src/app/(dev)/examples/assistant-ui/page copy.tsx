"use client"
"use client"
/**
 * assistant-ui 示例页——使用 useLocalRuntime + postAiStream 对接 /api/chat/run 流式接口（自动携带 Bearer token）
 * 路由：/dev/examples/assistant-ui
 * @author AaronZZH & Kiro
 */

import type { ChatModelAdapter } from "@assistant-ui/react"
import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useLocalRuntime
} from "@assistant-ui/react"
import { useEffect, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { postAiStream } from "@/lib/api/ai-stream"
import { type AiModelVO, listTextModels } from "@/lib/api/rest/ai/ai-model"

export default function AssistantUIExamplePage() {
  const [models, setModels] = useState<AiModelVO[]>([])
  const [modelId, setModelId] = useState<string>("")
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])

  useEffect(() => {
    listTextModels()
      .then(setModels)
      .catch(() => {})
  }, [])

  /** adapter 持有 modelId ref，保证每次发送都用最新值 */
  const modelIdRef = useRef(modelId)
  useEffect(() => {
    modelIdRef.current = modelId
  }, [modelId])

  const chatModelAdapter: ChatModelAdapter = {
    async *run({ messages, abortSignal }) {
      const body = {
        threadId: `thread-${Date.now()}`,
        modelId: modelIdRef.current,
        messages: messages.map((m) => ({
          role: m.role,
          content: m.content
            .filter((p) => p.type === "text")
            .map((p) => p.text)
            .join("")
        })),
        target: { type: "ai" },
        state: { persist: false }
      }

      let content = ""
      let resolver: ((v: string | null) => void) | null = null
      const queue: (string | null)[] = []

      const enqueue = (val: string | null) => {
        if (resolver) {
          const r = resolver
          resolver = null
          r(val)
        } else queue.push(val)
      }

      const streamPromise = postAiStream("/chat/run", body, {
        onChunk: (text) => {
          content += text
          enqueue(content)
        },
        onDone: () => enqueue(null),
        onError: (err) => {
          throw err
        },
        signal: abortSignal ?? undefined
      })

      while (true) {
        const next: string | null =
          queue.length > 0
            ? (queue.shift() as string | null)
            : await new Promise<string | null>((r) => {
                resolver = r
              })
        if (next === null) break
        yield { content: [{ type: "text" as const, text: next }] }
      }

      await streamPromise
    }
  }

  const runtime = useLocalRuntime(chatModelAdapter)

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>assistant-ui 示例</TypographyH1>
        <TypographyMuted>
          使用 useLocalRuntime + ChatModelAdapter 对接 /api/chat/run 流式接口
        </TypographyMuted>
      </div>

      {/* 模型选择 */}
      <div className="mb-4 flex items-center gap-2">
        <span className="text-muted-foreground text-sm">对话模型：</span>
        <Select value={modelId} onValueChange={(v) => v && setModelId(v)}>
          <SelectTrigger className="w-56">
            <SelectValue placeholder="选择模型">
              {modelId ? models.find((m) => m.modelId === modelId)?.displayName : "系统默认"}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">系统默认</SelectItem>
            {models.map((m) => (
              <SelectItem key={m.modelId} value={m.modelId}>
                {m.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-xl border bg-card">
        {mounted && (
          <AssistantRuntimeProvider runtime={runtime}>
            <ThreadPrimitive.Root>
              <ThreadPrimitive.Viewport className="flex h-[500px] flex-col overflow-y-auto p-4">
                <ThreadPrimitive.Empty>
                  <div className="flex flex-1 items-center justify-center text-muted-foreground text-sm">
                    发送一条消息开始对话
                  </div>
                </ThreadPrimitive.Empty>
                <ThreadPrimitive.Messages components={{ UserMessage, AssistantMessage }} />
              </ThreadPrimitive.Viewport>
              <Composer />
            </ThreadPrimitive.Root>
          </AssistantRuntimeProvider>
        )}
      </div>
    </PageContainer>
  )
}

function UserMessage() {
  return (
    <div className="mb-3 flex justify-end">
      <div className="max-w-[80%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm">
        <MessagePrimitive.Content />
      </div>
    </div>
  )
}

function AssistantMessage() {
  return (
    <div className="mb-3 flex justify-start">
      <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm">
        <MessagePrimitive.Content />
      </div>
    </div>
  )
}

function Composer() {
  return (
    <ComposerPrimitive.Root className="flex items-end gap-2 border-t p-3">
      <ComposerPrimitive.Input
        placeholder="输入消息..."
        className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
      />
      <ComposerPrimitive.Send className="inline-flex h-7 items-center rounded-lg bg-primary px-2.5 font-medium text-[0.8rem] text-primary-foreground transition-opacity disabled:opacity-50">
        发送
      </ComposerPrimitive.Send>
    </ComposerPrimitive.Root>
  )
}
