"use client"
/**
 * AgentScope 示例页——对接 /api/examples/agentscope/* REST 接口
 * 普通 POST 请求/响应模式（非 SSE），需后端配置 aaf.examples.agentscope.enabled=true
 * 路由：/dev/examples/agentscope
 * @author AaronZZH & Kiro
 */

import { useState, useEffect } from "react"
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
    desc: "多智能体协作",
    hint: "多 Agent 协作处理",
    defaultMsg: "从技术、商业、用户体验三个角度分析 AI 助手的价值"
  },
  {
    id: "pipeline",
    label: "Pipeline",
    desc: "顺序管道",
    hint: "依次处理",
    defaultMsg: "依次完成：总结、翻译成英文、润色这段话：AI 是未来的核心生产力"
  },
  {
    id: "debate",
    label: "MsgHub 辩论",
    desc: "多 Agent 辩论",
    hint: "正反辩论",
    defaultMsg: "正方和反方分别阐述：AI 是否会取代人类工作"
  },
  {
    id: "session-chat",
    label: "Session 持久化",
    desc: "跨请求记忆",
    hint: "先记住名字，再第二条消息提问验证记忆",
    defaultMsg: "我叫张三，请记住我的名字"
  },
  {
    id: "mcp-tool",
    label: "MCP 工具",
    desc: "需配置 MCP Server URL",
    hint: "调用外部 MCP 工具",
    defaultMsg: "帮我查询当前天气"
  },
  {
    id: "rag-chat",
    label: "RAG 知识库",
    desc: "需 DashScope Embedding",
    hint: "从知识库检索回答",
    defaultMsg: "AAF 框架有哪些核心能力？"
  },
  {
    id: "plan-chat",
    label: "Plan 任务规划",
    desc: "⚠️ 暂不可用（Jackson 兼容性）",
    hint: "PlanNotebook 内置工具依赖 victools，与 Jackson 3.x 不兼容，待 agentscope 升级修复",
    defaultMsg: "帮我规划一个 30 天学习 AI 框架的计划"
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
  const [sendCount, setSendCount] = useState(0)
  const [cooldown, setCooldown] = useState(0)
  const selected = AGENTS.find((a) => a.id === selectedId)!

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
      const body =
        selectedId === "debate"
          ? { topic: userMsg, rounds: 2 }
          : selectedId === "session-chat"
          ? { sessionId: "demo-session", input: userMsg }
          : { input: userMsg }
      const res = await request<string | Record<string, unknown>>(`/examples/agentscope/${selectedId}`, {
        method: "POST",
        body: JSON.stringify(body)
      })
      const content = typeof res === "string"
        ? res
        : selectedId === "session-chat" && "reply" in res
        ? `${res.reply}\n\n---\n📋 会话状态：${res.isNew ? "新建" : "已恢复"} | 历史消息数：${res.historyMessages}`
        : JSON.stringify(res, null, 2)
      setMessages((prev) => [...prev, { role: "assistant", content: content ?? "(无回复)" }])
      // 每2次触发5秒冷却
      setSendCount((c) => {
        const next = c + 1
        if (next % 10 === 0) setCooldown(5)
        return next
      })
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
