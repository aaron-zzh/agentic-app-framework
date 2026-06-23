/**
 * AI 技能 TanStack Query Hooks
 *
 * 调用后端 SkillController：GET /api/system/skills/active
 * 字段与后端 SkillVO（apps/service/.../module/ai/skill/SkillVO.java）严格对齐。
 *
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery } from "@/lib/api/rest/crud/client"

/** 与后端 SkillVO 字段对齐 */
export interface AiSkillVO {
  id: number
  /** 业务唯一码（前端 deep-link 用，可为空） */
  code: string | null
  name: string
  description: string | null
  /** 技能分类：COPYWRITING/STRATEGY/... */
  category: string | null
  agentId: number | null
  triggerIntent: string | null
  systemPrompt: string | null
  priority: number
  /** 是否内置（后端字段名 builtIn，对齐 SkillVO） */
  builtIn: boolean
  /** 状态：active / inactive */
  status: string
}

interface AiSkillsParams {
  /** 按分类过滤（如 COPYWRITING） */
  category?: string
  /** 是否只返回 status='active'，默认 true */
  activeOnly?: boolean
}

/**
 * 查询技能列表，按 priority 降序。
 *
 * 路径：GET /api/system/skills/active（仅返回全局技能 owner_id IS NULL）
 */
export function useAiSkills(params: AiSkillsParams = {}) {
  return useQuery({
    queryKey: ["ai-skills", params] as const,
    queryFn: () =>
      backendApi.get<AiSkillVO[]>(
        `/system/skills/active${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
      ),
    staleTime: 5 * 60 * 1000
  })
}
