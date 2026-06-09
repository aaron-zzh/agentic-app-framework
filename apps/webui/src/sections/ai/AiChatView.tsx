/**
 * AiChatView——AI 对话全屏分栏视图
 *
 * 布局：左侧会话列表 + 右侧 Chatter panel（assistant-ui）
 * 使用 ResizablePanelGroup 支持拖拽调整分栏宽度
 *
 * 注意：此页面使用 layout="panel"，WorkspaceLayout 的
 * 右侧 Copilot 面板会被此页面自己的 Chatter 替代，无需额外开关。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MessageSquare, Plus } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Chatter, useChatterLayoutPreference } from "@/features/chatter"
import { cn } from "@/lib/utils/cn"

interface Session {
  id: string
  title: string
  preview: string
  updatedAt: string
}

/** Mock 会话列表（后续接入真实 chatApi.getSessions） */
const MOCK_SESSIONS: Session[] = [
  { id: "1", title: "AAF 架构设计讨论", preview: "你好，请帮我分析一下...", updatedAt: "刚刚" },
  {
    id: "2",
    title: "工作流引擎调研",
    preview: "Flowable 和 Camunda 的区别...",
    updatedAt: "2小时前"
  },
  { id: "3", title: "知识库检索优化", preview: "向量检索的召回率问题...", updatedAt: "昨天" }
]

export function AiChatView() {
  const [activeSessionId, setActiveSessionId] = useState<string>(MOCK_SESSIONS[0].id)
  // page 模式：WorkspaceLayout 不渲染任何 Chatter UI，此页面完全自管
  useChatterLayoutPreference("page")

  return (
    <div className="flex h-full overflow-hidden rounded-lg border bg-background shadow-sm">
      <ResizablePanelGroup orientation="horizontal" className="h-full">
        {/* 左侧：会话列表 */}
        <ResizablePanel defaultSize="22%" minSize="16%" maxSize="35%" className="flex flex-col">
          {/* 列表头 */}
          <div className="flex h-14 shrink-0 items-center justify-between border-b px-4">
            <span className="font-semibold text-sm">AI 对话</span>
            <Button variant="ghost" size="icon-sm" aria-label="新建对话">
              <Plus className="size-4" />
            </Button>
          </div>

          {/* 会话列表 */}
          <div className="flex-1 overflow-y-auto py-2">
            {MOCK_SESSIONS.map((session) => (
              <button
                key={session.id}
                type="button"
                onClick={() => setActiveSessionId(session.id)}
                className={cn(
                  "flex w-full flex-col gap-0.5 px-4 py-3 text-left transition-colors hover:bg-accent",
                  activeSessionId === session.id && "bg-accent"
                )}
              >
                <div className="flex items-center gap-2">
                  <MessageSquare className="size-3.5 shrink-0 text-muted-foreground" />
                  <span className="truncate font-medium text-sm">{session.title}</span>
                </div>
                <p className="truncate pl-5 text-muted-foreground text-xs">{session.preview}</p>
                <span className="pl-5 text-[10px] text-muted-foreground/60">
                  {session.updatedAt}
                </span>
              </button>
            ))}
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* 右侧：Chatter 对话面板 */}
        <ResizablePanel defaultSize="78%" minSize="50%">
          <Chatter preset="ai" layout="panel" sessionId={activeSessionId} />
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
