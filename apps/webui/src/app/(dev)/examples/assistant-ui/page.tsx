"use client"

/**
 * assistant-ui 示例页——使用 useLocalRuntime 对接 /api/chat 流式接口
 * 路由：/dev/examples/assistant-ui
 * @author AaronZZH & Kiro
 */

import {
  AssistantRuntimeProvider,
  useLocalRuntime,
  ThreadPrimitive,
  MessagePrimitive,
  ComposerPrimitive,
} from "@assistant-ui/react"
import type { ChatModelAdapter } from "@assistant-ui/react"
import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { Button } from "@/components/ui/button"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

/**
 * 自定义 ChatModelAdapter，对接 /api/chat SSE 流式接口
 */
const chatModelAdapter: ChatModelAdapter = {
  async *run({ messages, abortSignal }) {
    const body = JSON.stringify({
      messages: messages.map((m) => ({
        role: m.role,
        content: m.content
          .filter((p) => p.type === "text")
          .map((p) => p.text)
          .join(""),
      })),
    })

    const response = await fetch(`${API_URL}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      signal: abortSignal,
    })

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error("无法读取响应流")

    const decoder = new TextDecoder()
    let content = ""

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split("\n")

      for (const line of lines) {
        if (!line.startsWith("data: ")) continue
        const data = line.slice(6).trim()
        if (data === "[DONE]") break

        try {
          const parsed = JSON.parse(data) as {
            choices: { delta: { content?: string } }[]
          }
          const delta = parsed.choices[0]?.delta?.content
          if (delta) {
            content += delta
            yield { content: [{ type: "text" as const, text: content }] }
          }
        } catch {
          // 忽略解析错误
        }
      }
    }
  },
}

export default function AssistantUIExamplePage() {
  const runtime = useLocalRuntime(chatModelAdapter)

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>assistant-ui 示例</TypographyH1>
        <TypographyMuted>
          使用 useLocalRuntime + ChatModelAdapter 对接 /api/chat 流式接口
        </TypographyMuted>
      </div>

      <div className="rounded-xl border bg-card">
        <AssistantRuntimeProvider runtime={runtime}>
          <ThreadPrimitive.Root>
            <ThreadPrimitive.Viewport className="flex h-[500px] flex-col overflow-y-auto p-4">
              <ThreadPrimitive.Empty>
                <div className="flex flex-1 items-center justify-center text-muted-foreground text-sm">
                  发送一条消息开始对话
                </div>
              </ThreadPrimitive.Empty>
              <ThreadPrimitive.Messages
                components={{ UserMessage, AssistantMessage }}
              />
            </ThreadPrimitive.Viewport>
            <Composer />
          </ThreadPrimitive.Root>
        </AssistantRuntimeProvider>
      </div>
    </PageContainer>
  )
}

/** 用户消息气泡 */
function UserMessage() {
  return (
    <div className="mb-3 flex justify-end">
      <div className="max-w-[80%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm">
        <MessagePrimitive.Content />
      </div>
    </div>
  )
}

/** AI 消息气泡 */
function AssistantMessage() {
  return (
    <div className="mb-3 flex justify-start">
      <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm">
        <MessagePrimitive.Content />
      </div>
    </div>
  )
}

/** 输入框组合 */
function Composer() {
  return (
    <ComposerPrimitive.Root className="flex items-end gap-2 border-t p-3">
      <ComposerPrimitive.Input
        placeholder="输入消息..."
        className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
      />
      <ComposerPrimitive.Send asChild>
        <Button size="sm">发送</Button>
      </ComposerPrimitive.Send>
    </ComposerPrimitive.Root>
  )
}
