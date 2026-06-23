"use client"
/**
 * AgentScope 示例页——对接 /api/examples/agentscope/* REST 接口
 * 普通 POST 请求/响应模式（非 SSE），需后端配置 aaf.examples.agentscope.enabled=true
 * 路由：/dev/examples/agentscope
 * @author AaronZZH & Kiro
 */

import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { request } from "@/lib/api/rest/entity/crud"

const AGENTS = [
  {
    id: "basic-chat",
    label: "基础聊天",
    desc: "纯对话",
    hint: "直接发送任意消息",
    defaultMsg: "你好，介绍一下你自己"
  },
  {
    id: "tool-calling",
    label: "工具调用",
    desc: "数学计算 + 时间查询",
    hint: "调用工具",
    defaultMsg: "计算 123 乘以 456，并告诉我现在几点"
  },
  {
    id: "supervisor",
    label: "Supervisor",
    desc: "主 Agent 委托子 Agent 处理日程",
    hint: "多 Agent 协作",
    defaultMsg: "帮我查询明天的可用时间，并安排一个下午 2 点的会议"
  },
  {
    id: "pipeline",
    label: "Pipeline",
    desc: "自然语言 → SQL 生成 → 质量评分",
    hint: "多 Agent 串联",
    defaultMsg: "查询所有年龄大于 30 岁的用户姓名和邮箱"
  },
  {
    id: "mcp-tool",
    label: "MCP 工具",
    desc: "需配置 MCP Server URL",
    hint: "调用外部 MCP 工具",
    defaultMsg: "帮我查询当前天气"
  }
]

interface Message {
  role: "user" | "assistant"
  content: string
}

export default function AgentScopeExamplePage() {
  const [selectedId, setSelectedId] = useState(AGENTS[0].id)
  const [input, setInput] = useState("")
  const [messages, setMessages] = useState<Message[]>([])
  const [loading, setLoading] = useState(false)
  const sendCountRef = useRef(0)
  const [cooldown, setCooldown] = useState(0)
  const selected = AGENTS.find((a) => a.id === selectedId) ?? AGENTS[0]

  // 冷却倒计时
  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const handleSend = async () => {
    if (!input.trim() || loading || cooldown > 0) return
    const userMsg = input.trim()
    setInput("")
    setMessages((prev) => [...prev, { role: "user", content: userMsg }])
    setLoading(true)
    try {
      const body = { input: userMsg }
      const res = await request<string | Record<string, unknown>>(
        `/examples/agentscope/${selectedId}`,
        {
          method: "POST",
          body: JSON.stringify(body)
        }
      )
      const content = typeof res === "string" ? res : JSON.stringify(res, null, 2)
      setMessages((prev) => [...prev, { role: "assistant", content: content ?? "(无回复)" }])
      // 每2次触发5秒冷却
      sendCountRef.current += 1
      if (sendCountRef.current % 10 === 0) setCooldown(5)
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: `错误：${e instanceof Error ? e.message : String(e)}` }
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex h-[calc(100vh-3rem)] flex-col">
      {/* Agent 选择栏 */}
      <div className="flex flex-wrap items-center gap-2 border-b px-6 py-3">
        {AGENTS.map((agent) => (
          <Button
            key={agent.id}
            variant={selectedId === agent.id ? "default" : "ghost"}
            size="sm"
            onClick={() => {
              setSelectedId(agent.id)
              setMessages([])
            }}
          >
            {agent.label}
          </Button>
        ))}
        <span className="ml-2 text-muted-foreground text-xs">{selected.desc}</span>
      </div>

      {/* 消息列表 */}
      <div className="flex-1 space-y-3 overflow-y-auto p-6">
        {/* 可点击的预设消息 */}
        <Button
          variant="outline"
          className="h-auto w-full justify-start border-dashed bg-muted/50 px-4 py-3 text-left font-normal text-muted-foreground text-sm hover:bg-muted hover:text-foreground"
          onClick={() => setInput(selected.defaultMsg)}
        >
          💡 {selected.hint}：<span className="font-medium">"{selected.defaultMsg}"</span>
          <span className="ml-2 text-xs opacity-60">点击填入</span>
        </Button>
        {messages.length === 0 && (
          <p className="text-center text-muted-foreground text-sm">发送消息开始测试</p>
        )}
        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-2xl whitespace-pre-wrap rounded-lg px-4 py-2 text-sm ${
                msg.role === "user" ? "bg-primary text-primary-foreground" : "bg-muted"
              }`}
            >
              {msg.content}
            </div>
          </div>
        ))}
        {loading && (
          <div className="flex justify-start">
            <div className="rounded-lg bg-muted px-4 py-2 text-muted-foreground text-sm">
              思考中…
            </div>
          </div>
        )}
      </div>

      {/* 输入区 */}
      <div className="flex gap-2 border-t p-4">
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入消息…"
          className="min-h-0 flex-1 resize-none"
          rows={2}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault()
              handleSend()
            }
          }}
        />
        <Button onClick={handleSend} disabled={loading || !input.trim() || cooldown > 0}>
          {cooldown > 0 ? `冷却 ${cooldown}s` : "发送"}
        </Button>
      </div>
    </div>
  )
}
