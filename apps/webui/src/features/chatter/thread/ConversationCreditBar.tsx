/**
 * 对话积分条：固定在输入框上方，展示本轮消耗与当前对话累计积分。
 *
 * 不随消息滚动；生成中不请求，本轮结束后刷新。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useAuiState } from "@assistant-ui/react"
import { useAgentRunStore } from "@/features/livechat/runtime/agent-run-store"
import { useConversationCreditSummary } from "@/lib/api/rest/ai"

export function ConversationCreditBar() {
  const isRunning = useAuiState((s) => s.thread.isRunning)
  const currentThreadId = useAgentRunStore((state) => state.currentThreadId)
  const currentRunId = useAgentRunStore((state) => state.diagnostic.runId)
  const { data: creditSummary } = useConversationCreditSummary(
    currentThreadId,
    currentRunId,
    !isRunning
  )

  if (!creditSummary) return null

  return (
    <div className="flex shrink-0 justify-center gap-1.5 px-3 pb-1 text-[11px] text-muted-foreground/70">
      <span>本轮消耗 {creditSummary.turnCredits.toLocaleString()} 积分</span>
      <span aria-hidden="true">·</span>
      <span>当前对话累计 {creditSummary.conversationCredits.toLocaleString()} 积分</span>
    </div>
  )
}
