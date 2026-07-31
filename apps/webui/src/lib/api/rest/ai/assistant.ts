/**
 * 可用助理 API、类型与 TanStack Query Hook
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

/** 助理下的角色条目 */
export interface RoleItem {
  roleId: string
  name: string
  description?: string
}

/** 助理列表项（含角色列表） */
export interface AssistantItem {
  assistantId: string
  name: string
  avatar?: string
  defaultRoleId: string
  roles: RoleItem[]
}

export const assistantApi = {
  listAvailable: (): Promise<AssistantItem[]> => backendApi.get("/ai/assistants/available")
}

/** 查询当前用户可用的助理列表（含各助理下的角色） */
export function useAssistants() {
  return useQuery({
    queryKey: ["ai", "assistants", "available"],
    queryFn: assistantApi.listAvailable
  })
}
