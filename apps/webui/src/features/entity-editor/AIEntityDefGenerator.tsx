/**
 * AI 对话生成 EntityDef 组件
 * 用户通过自然语言描述需求，AI 生成完整 EntityDef JSON，支持追加修改和实时预览。
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Copy, Loader2, Send, Sparkles } from "lucide-react"
import { type KeyboardEvent, useCallback, useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import type { EntityDefConfig } from "@/features/entity-engine/types"
import { useEntityBootstrap } from "@/lib/api/rest/entity/entity-def"

import {
  buildEntityDefGeneratorSystemPrompt,
  extractGeneratedEntityDefJson,
  validateGeneratedEntityDef
} from "./entity-def-generation"

/** 对话消息 */
interface ChatMessage {
  id: string
  role: "user" | "assistant"
  content: string
}

interface ChatCompletionChunk {
  choices?: Array<{ delta?: { content?: string } }>
}

interface AIEntityDefGeneratorProps {
  /** 将生成的 JSON 应用到编辑器 */
  onApply: (json: string) => void
}

/** AI 对话生成 EntityDef 组件 */
export function AIEntityDefGenerator({ onApply }: AIEntityDefGeneratorProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)
  const [generatedJson, setGeneratedJson] = useState<string | null>(null)
  const [previewEntity, setPreviewEntity] = useState<EntityDefConfig | null>(null)
  const [validationErrors, setValidationErrors] = useState<string[]>([])
  const {
    data: bootstrap,
    isError: bootstrapError,
    isLoading: bootstrapLoading
  } = useEntityBootstrap()
  const resources = bootstrap?.resources ?? []
  const resourceContextReady = !bootstrapLoading && !bootstrapError && resources.length > 0
  const systemPrompt = useMemo(
    () => buildEntityDefGeneratorSystemPrompt(resources, generatedJson),
    [generatedJson, resources]
  )

  /** 发送消息并流式接收 AI 响应。 */
  const handleSend = useCallback(async () => {
    const content = input.trim()
    if (!content || loading) return

    const userMsg: ChatMessage = { id: crypto.randomUUID(), role: "user", content }
    const newMessages = [...messages, userMsg]
    setMessages(newMessages)
    setInput("")
    setValidationErrors([])

    if (!resourceContextReady) {
      const error = bootstrapError
        ? "可信资源目录加载失败，无法安全生成配置。"
        : "正在加载可信资源目录，请稍后重试。"
      setMessages((previous) => [
        ...previous,
        { id: crypto.randomUUID(), role: "assistant", content: `❌ ${error}` }
      ])
      return
    }

    setLoading(true)
    const apiMessages = [
      { role: "system", content: systemPrompt },
      ...newMessages.map((message) => ({ role: message.role, content: message.content }))
    ]

    const assistantId = crypto.randomUUID()
    let fullContent = ""

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: apiMessages })
      })
      if (!res.ok || !res.body) throw new Error(`请求失败: ${res.statusText}`)

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        for (const line of decoder.decode(value, { stream: true }).split("\n")) {
          if (!line.startsWith("data: ") || line === "data: [DONE]") continue
          try {
            const data = JSON.parse(line.slice(6)) as ChatCompletionChunk
            const token = data.choices?.[0]?.delta?.content ?? ""
            fullContent += token
            setMessages((previous) => {
              const existing = previous.find((message) => message.id === assistantId)
              if (existing) {
                return previous.map((message) =>
                  message.id === assistantId ? { ...message, content: fullContent } : message
                )
              }
              return [...previous, { id: assistantId, role: "assistant", content: fullContent }]
            })
          } catch {
            // 忽略不符合 SSE JSON 格式的分片。
          }
        }
      }

      const json = extractGeneratedEntityDefJson(fullContent)
      if (!json) {
        setValidationErrors(["模型未返回可解析的单个 JSON 配置代码块"])
        return
      }
      const result = validateGeneratedEntityDef(JSON.parse(json) as unknown, resources)
      setValidationErrors(result.errors)
      if (result.config) {
        setGeneratedJson(json)
        setPreviewEntity(result.config)
      }
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "请求失败"
      setMessages((previous) => [
        ...previous,
        { id: assistantId, role: "assistant", content: `❌ ${errorMsg}` }
      ])
    } finally {
      setLoading(false)
    }
  }, [bootstrapError, input, loading, messages, resourceContextReady, resources, systemPrompt])

  /** 只允许应用通过可信资源和字段校验的配置。 */
  const handleApply = useCallback(() => {
    if (generatedJson && validationErrors.length === 0 && !loading) onApply(generatedJson)
  }, [generatedJson, loading, onApply, validationErrors.length])

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault()
        handleSend()
      }
    },
    [handleSend]
  )

  const inputDisabled = loading || !resourceContextReady
  const resourceStatus = bootstrapLoading
    ? "正在加载可信资源"
    : bootstrapError
      ? "可信资源加载失败"
      : `可信资源 ${resources.length}`

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b px-4 py-2">
        <Sparkles className="h-4 w-4 text-primary" />
        <span className="font-medium text-sm">AI 生成 EntityDef</span>
        <Badge variant={bootstrapError ? "destructive" : "secondary"}>{resourceStatus}</Badge>
        {previewEntity && <Badge variant="secondary">{previewEntity.label}</Badge>}
      </div>

      <div className="flex flex-1 overflow-hidden">
        <div className="flex w-1/2 flex-col border-r">
          <ScrollArea className="flex-1 p-4">
            {messages.length === 0 && (
              <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                <Bot className="h-8 w-8" />
                <p className="text-sm">描述已注册资源的展示需求，AI 将生成配置草稿</p>
                <p className="text-xs">AI 只能引用服务端提供的资源与字段白名单</p>
              </div>
            )}
            <div className="space-y-4">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
                      message.role === "user"
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-foreground"
                    }`}
                  >
                    {message.content}
                  </div>
                </div>
              ))}
              {loading && (
                <div className="flex justify-start">
                  <div className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-muted-foreground text-sm">
                    <Loader2 className="h-3 w-3 animate-spin" />
                    生成中...
                  </div>
                </div>
              )}
            </div>
          </ScrollArea>

          <div className="border-t p-3">
            <div className="flex gap-2">
              <Textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={
                  resourceContextReady
                    ? "描述你要调整的已注册资源视图...（Enter 发送，Shift+Enter 换行）"
                    : "等待可信资源目录加载..."
                }
                className="min-h-[60px] resize-none"
                disabled={inputDisabled}
              />
              <Button
                size="icon"
                onClick={handleSend}
                disabled={!input.trim() || inputDisabled}
                className="shrink-0 self-end"
              >
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>

        <div className="flex w-1/2 flex-col">
          {generatedJson && (
            <div className="flex items-center gap-2 border-b px-4 py-2">
              <Button
                size="sm"
                variant="outline"
                onClick={handleApply}
                disabled={loading || validationErrors.length > 0}
              >
                <Copy className="mr-1 h-3 w-3" />
                应用到编辑器
              </Button>
            </div>
          )}

          <ScrollArea className="flex-1 p-4">
            {validationErrors.length > 0 ? (
              <div className="space-y-2 rounded-md border border-destructive/40 bg-destructive/5 p-3">
                <p className="font-medium text-destructive text-sm">生成配置未通过可信校验</p>
                <ul className="list-disc space-y-1 pl-4 text-muted-foreground text-xs">
                  {validationErrors.map((error) => (
                    <li key={error}>{error}</li>
                  ))}
                </ul>
              </div>
            ) : previewEntity ? (
              <div className="space-y-4">
                <p className="font-medium text-muted-foreground text-xs">
                  配置已通过资源与字段校验
                </p>
                <p className="text-muted-foreground text-sm">
                  slug、apiPath 与关系选择器路径仍由服务端在读取时按 resource 投影。
                </p>
                <details>
                  <summary className="cursor-pointer text-muted-foreground text-xs">
                    查看 JSON 配置
                  </summary>
                  <pre className="mt-2 overflow-auto rounded-md bg-muted p-3 text-xs">
                    {generatedJson}
                  </pre>
                </details>
              </div>
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
                AI 生成的配置将在此处预览
              </div>
            )}
          </ScrollArea>
        </div>
      </div>
    </div>
  )
}
