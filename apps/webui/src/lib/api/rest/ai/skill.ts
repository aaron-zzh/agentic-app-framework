/**
 * AI 技能 API、DTO 与 TanStack Query Hook
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"
import { buildQuery } from "../crud/client"

export interface AiSkillVO {
  id: number
  code: string | null
  name: string
  description: string | null
  category: string | null
  agentId: number | null
  triggerIntent: string | null
  systemPrompt: string | null
  priority: number
  builtIn: boolean
  status: string
}

export interface AiSkillsParams {
  category?: string
  activeOnly?: boolean
}

export const skillApi = {
  listActive: (params: AiSkillsParams = {}): Promise<AiSkillVO[]> =>
    backendApi.get(
      `/system/skills/active${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
    )
}

export function useAiSkills(params: AiSkillsParams = {}) {
  return useQuery({
    queryKey: ["ai-skills", params] as const,
    queryFn: () => skillApi.listActive(params),
    staleTime: 5 * 60 * 1000
  })
}
