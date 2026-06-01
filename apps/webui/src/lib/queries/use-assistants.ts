/**
 * 助理列表 TanStack Query Hook
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { request } from "@/lib/api/rest/entity/crud"

/** 助理列表项（前端展示用，含 actor 头像） */
export interface AssistantItem {
  assistantId: string
  roleId: string
  actorId: string
  name: string
  avatar?: string
}

/** 查询当前用户可用的助理列表 */
export function useAssistants() {
  return useQuery({
    queryKey: ["ai", "assistants", "available"],
    queryFn: () => request<AssistantItem[]>("/ai/assistants/available")
  })
}
