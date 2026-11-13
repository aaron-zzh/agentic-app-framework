/**
 * 对话积分：显示在对话流最后一条回复的操作栏旁，展示本轮消耗与当前对话累计积分。
 *
 * 生成中不请求，本轮结束后刷新。
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
    <div className="flex shrink-0 gap-1.5 text-[11px] text-muted-foreground/70">
      <span>本轮 {creditSummary.turnCredits.toLocaleString()}</span>
      <span aria-hidden="true">/</span>
      <span>{creditSummary.conversationCredits.toLocaleString()} 积分</span>
    </div>
  )
}
