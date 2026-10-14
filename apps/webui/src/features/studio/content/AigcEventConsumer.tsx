/**
 * Studio 的 AIGC 事件消费者：订阅全局事件通道中 `aigc.` 前缀的事件，统一失效权威查询。
 *
 * 连接由 lib/realtime 的全局 Provider 持有；事件仅作为变化通知，业务 payload 不写入本地状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { type QueryClient, useQueryClient } from "@tanstack/react-query"
import { aigcExecutionKeys } from "@/lib/api/rest/ai/aigc/execution"
import { aigcProjectKeys } from "@/lib/api/rest/ai/aigc/project"
import { aigcWorkKeys } from "@/lib/api/rest/ai/aigc/work"
import { taskKeys } from "@/lib/api/rest/ai/task"
import { MEDIA_QUERY_KEY } from "@/lib/api/rest/media/media-asset"
import type { RealtimeEvent } from "@/lib/realtime/event-stream"
import { useEventSubscription } from "@/lib/realtime/use-event-subscription"

const AIGC_EVENT_PREFIX = "aigc."

/** 按事件 refs 失效 AIGC 相关查询；无 projectId 时失效全部项目查询。 */
export function invalidateAigcQueries(queryClient: QueryClient, event: RealtimeEvent): void {
  queryClient.invalidateQueries({ queryKey: aigcExecutionKeys.all })
  queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
  queryClient.invalidateQueries({ queryKey: MEDIA_QUERY_KEY })
  queryClient.invalidateQueries({ queryKey: taskKeys.all })
  queryClient.invalidateQueries({ queryKey: ["aigc.task"] })
  const projectId = event.refs.projectId
  if (projectId === undefined) {
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
    return
  }
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.conversationContext(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) })
  queryClient.invalidateQueries({ queryKey: ["aigc.project", "readiness", projectId] })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.progress(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.generationProposals(projectId) })
  queryClient.invalidateQueries({ queryKey: ["aigc.project", "document-candidates", projectId] })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.documentRefs(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.mediaRefs(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.reviews(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.completionEvidence(projectId) })
  queryClient.invalidateQueries({ queryKey: ["aigc.project", "versions", projectId] })
}

export function AigcEventConsumer() {
  const queryClient = useQueryClient()
  useEventSubscription(AIGC_EVENT_PREFIX, (event) => invalidateAigcQueries(queryClient, event))
  return null
}
