/**
 * 对话式流程交互组件——展示工作流执行消息 + 用户输入 + 审批操作
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { WorkflowMessage, WorkflowRunStatus } from "../hooks/use-workflow-runtime"

interface WorkflowChatProps {
  messages: WorkflowMessage[]
  status: WorkflowRunStatus
  onSubmitInput: (input: string) => void
  /** 审批模式：展示同意/拒绝按钮 */
  approvalMode?: boolean
}

export function WorkflowChat({ messages, status, onSubmitInput, approvalMode }: WorkflowChatProps) {
  const [input, setInput] = useState("")
  const scrollRef = useRef<HTMLDivElement>(null)

  /** 自动滚动到底部 */
  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [])

  const handleSubmit = () => {
    if (!input.trim()) return
    onSubmitInput(input.trim())
    setInput("")
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* 消息列表 */}
      <ScrollArea className="flex-1 p-4">
        <div className="space-y-3">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                  msg.role === "user" ? "bg-primary text-primary-foreground" : "bg-muted"
                }`}
              >
                {msg.content}
              </div>
            </div>
          ))}
          {status === "running" && (
            <div className="flex justify-start">
              <Badge variant="secondary" className="animate-pulse">
                执行中...
              </Badge>
            </div>
          )}
          <div ref={scrollRef} />
        </div>
      </ScrollArea>

      {/* 输入区域 */}
      {status === "waiting_input" && (
        <div className="border-t p-3">
          {approvalMode ? (
            <div className="flex gap-2">
              <Button
                className="flex-1"
                variant="default"
                onClick={() => onSubmitInput("approved")}
              >
                同意
              </Button>
              <Button
                className="flex-1"
                variant="destructive"
                onClick={() => onSubmitInput("rejected")}
              >
                拒绝
              </Button>
            </div>
          ) : (
            <div className="flex gap-2">
              <Input
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="输入内容..."
              />
              <Button onClick={handleSubmit} disabled={!input.trim()}>
                发送
              </Button>
            </div>
          )}
        </div>
      )}

      {/* 完成/失败状态 */}
      {status === "completed" && (
        <div className="border-t p-3 text-center">
          <Badge variant="secondary">流程已完成</Badge>
        </div>
      )}
      {status === "failed" && (
        <div className="border-t p-3 text-center">
          <Badge variant="destructive">流程执行失败</Badge>
        </div>
      )}
    </div>
  )
}
