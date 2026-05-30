/**
 * AI 对话生成 EntityDef 组件
 * 用户通过自然语言描述需求，AI 生成完整 EntityDef JSON，支持追加修改和实时预览。
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Check, Copy, Loader2, Send, Sparkles } from "lucide-react"
import { useCallback, useState } from "react"
import { ViewErrorBoundary } from "@/components/common/ViewErrorBoundary"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import { ViewEngine } from "@/features/entity-engine/components/ViewEngine"
import type { EntityDef } from "@/lib/types/entity"
import { useCreateEntityDef } from "@/lib/queries/use-entity-defs"
import { streamSSE } from "@/lib/utils/sse"

/** 对话消息 */
interface ChatMessage {
  id: string
  role: "user" | "assistant"
  content: string
}

interface AIEntityDefGeneratorProps {
  /** 将生成的 JSON 应用到编辑器 */
  onApply: (json: string) => void
}

/** 系统提示词：引导 AI 生成 EntityDef JSON */
const SYSTEM_PROMPT = `你是 AAF 框架的 EntityDef 配置生成助手。用户会用自然语言描述业务需求，你需要生成完整的 EntityDef JSON 配置。

要求：
1. 输出必须是合法的 JSON，用 \`\`\`json 代码块包裹
2. 必须包含 slug、label、apiPath、fields、listView
3. 字段类型支持：text、textarea、number、email、date、checkbox、select、relationship、richText、upload
4. select 类型必须包含 options 数组（每项有 label、value，可选 color）
5. listView 必须包含 columns、defaultSort
6. 如果用户要求修改，在之前的基础上增量修改，输出完整 JSON

示例输出格式：
\`\`\`json
{
  "slug": "customer",
  "label": "客户",
  "apiPath": "/api/customers",
  "fields": [...],
  "listView": { "columns": [...], "defaultSort": "createdAt:desc" }
}
\`\`\``

/**
 * 从 AI 响应中提取 JSON 代码块
 */
function extractJson(text: string): string | null {
  const match = text.match(/```json\s*([\s\S]*?)```/)
  if (match?.[1]) {
    try {
      JSON.parse(match[1].trim())
      return match[1].trim()
    } catch {
      return null
    }
  }
  // 尝试直接解析整段文本
  try {
    JSON.parse(text.trim())
    return text.trim()
  } catch {
    return null
  }
}

/** AI 对话生成 EntityDef 组件 */
export function AIEntityDefGenerator({ onApply }: AIEntityDefGeneratorProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)
  const [generatedJson, setGeneratedJson] = useState<string | null>(null)
  const [previewEntity, setPreviewEntity] = useState<EntityDef | null>(null)
  const createMutation = useCreateEntityDef()

  /** 发送消息并流式接收 AI 响应 */
  const handleSend = useCallback(async () => {
    const content = input.trim()
    if (!content || loading) return

    const userMsg: ChatMessage = { id: crypto.randomUUID(), role: "user", content }
    const newMessages = [...messages, userMsg]
    setMessages(newMessages)
    setInput("")
    setLoading(true)

    // 构建请求消息（含系统提示和历史）
    const apiMessages = [
      { role: "system", content: SYSTEM_PROMPT },
      ...newMessages.map((m) => ({ role: m.role, content: m.content }))
    ]

    const assistantId = crypto.randomUUID()
    let fullContent = ""

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: apiMessages })
      })

      if (!res.ok || !res.body) {
        throw new Error(`请求失败: ${res.statusText}`)
      }

      // 流式读取 SSE
      await streamSSE(res.body, {
        onData: (data) => {
          try {
            const parsed = JSON.parse(data)
            const token = parsed.choices?.[0]?.delta?.content ?? ""
            fullContent += token

            // 实时更新消息
            setMessages((prev) => {
              const existing = prev.find((m) => m.id === assistantId)
              if (existing) {
                return prev.map((m) => (m.id === assistantId ? { ...m, content: fullContent } : m))
              }
              return [...prev, { id: assistantId, role: "assistant", content: fullContent }]
            })
          } catch {
            // 忽略解析失败的行
          }
        }
      })

      // 提取 JSON 并更新预览
      const json = extractJson(fullContent)
      if (json) {
        setGeneratedJson(json)
        try {
          const parsed = JSON.parse(json) as EntityDef
          if (parsed.slug && parsed.fields) {
            setPreviewEntity(parsed)
          }
        } catch {
          // JSON 解析失败，不更新预览
        }
      }
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "请求失败"
      setMessages((prev) => [
        ...prev,
        { id: assistantId, role: "assistant", content: `❌ ${errorMsg}` }
      ])
    } finally {
      setLoading(false)
    }
  }, [input, loading, messages])

  /** 应用到编辑器 */
  const handleApply = useCallback(() => {
    if (generatedJson) {
      onApply(generatedJson)
    }
  }, [generatedJson, onApply])

  /** 保存并创建实体 */
  const handleSaveAndCreate = useCallback(async () => {
    if (!generatedJson) return
    try {
      const config = JSON.parse(generatedJson) as EntityDef
      await createMutation.mutateAsync({
        slug: config.slug,
        config: config as unknown as Record<string, unknown>,
        enabled: true
      })
    } catch {
      // 错误由 mutation 处理
    }
  }, [generatedJson, createMutation])

  /** 键盘快捷键：Enter 发送，Shift+Enter 换行 */
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault()
        handleSend()
      }
    },
    [handleSend]
  )

  return (
    <div className="flex h-full flex-col">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 border-b px-4 py-2">
        <Sparkles className="h-4 w-4 text-primary" />
        <span className="font-medium text-sm">AI 生成 EntityDef</span>
        {previewEntity && <Badge variant="secondary">{previewEntity.label}</Badge>}
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* 左侧：对话区 */}
        <div className="flex w-1/2 flex-col border-r">
          {/* 消息列表 */}
          <ScrollArea className="flex-1 p-4">
            {messages.length === 0 && (
              <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                <Bot className="h-8 w-8" />
                <p className="text-sm">描述你需要的业务模块，AI 将生成配置</p>
                <p className="text-xs">例如："创建一个客户管理模块，包含姓名、电话、状态字段"</p>
              </div>
            )}
            <div className="space-y-4">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
                      msg.role === "user"
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-foreground"
                    }`}
                  >
                    {msg.content}
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

          {/* 输入区 */}
          <div className="border-t p-3">
            <div className="flex gap-2">
              <Textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="描述你的需求...（Enter 发送，Shift+Enter 换行）"
                className="min-h-[60px] resize-none"
                disabled={loading}
              />
              <Button
                size="icon"
                onClick={handleSend}
                disabled={!input.trim() || loading}
                className="shrink-0 self-end"
              >
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>

        {/* 右侧：预览区 */}
        <div className="flex w-1/2 flex-col">
          {/* 操作按钮 */}
          {generatedJson && (
            <div className="flex items-center gap-2 border-b px-4 py-2">
              <Button size="sm" variant="outline" onClick={handleApply}>
                <Copy className="mr-1 h-3 w-3" />
                应用到编辑器
              </Button>
              <Button size="sm" onClick={handleSaveAndCreate} disabled={createMutation.isPending}>
                <Check className="mr-1 h-3 w-3" />
                {createMutation.isPending ? "保存中..." : "保存并创建"}
              </Button>
              {createMutation.isSuccess && <Badge variant="secondary">✓ 已创建</Badge>}
              {createMutation.isError && <Badge variant="destructive">创建失败</Badge>}
            </div>
          )}

          {/* 预览内容 */}
          <ScrollArea className="flex-1 p-4">
            {previewEntity ? (
              <div className="space-y-4">
                <p className="font-medium text-muted-foreground text-xs">视图预览</p>
                <ViewErrorBoundary>
                  <ViewEngine entity={previewEntity} view="list" />
                </ViewErrorBoundary>
                <details className="mt-4">
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
