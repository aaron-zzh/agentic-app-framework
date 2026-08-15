/**
 * AI 技能 API、DTO 与 TanStack Query Hook。
 *
 * @example
 * const { data } = useMyAiSkills({ category: "COPYWRITING", pageNo: 1, pageSize: 20 })
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

import { backendApi } from "../backend-client"
import { buildQuery, type CrudMeta } from "../crud/client"

const SKILL_PATH = "/system/skills"
const SKILL_QUERY_KEY = ["ai-skills"] as const

type SkillQueryParamValue = string | number | boolean | string[] | undefined

export type AiSkillStatus = "active" | "inactive"

export interface AiSkillVO {
  id: number
  version: number
  code: string | null
  name: string
  description: string | null
  category: string | null
  agentId: number | null
  triggerIntent: string | null
  systemPrompt: string | null
  priority: number
  builtIn: boolean
  isGlobal: boolean
  isPublic: boolean
  ownerId: number | null
  ownedByCurrentUser: boolean
  status: AiSkillStatus
  createTime: string
  updateTime: string
}

export interface AiSkillsParams extends Record<string, SkillQueryParamValue> {
  category?: string
  activeOnly?: boolean
}

export interface AiSkillDirectoryParams extends AiSkillsParams {
  search?: string
  pageNo?: number
  pageSize?: number
}

export interface CreateAiSkillInput {
  code?: string
  name: string
  description?: string
  category?: string
  agentId?: number
  triggerIntent?: string
  systemPrompt?: string
  priority?: number
  isPublic?: boolean
}

export type UpdateAiSkillInput = Partial<CreateAiSkillInput> & { status?: AiSkillStatus }

export const skillApi = {
  listActive: (params: AiSkillsParams = {}): Promise<AiSkillVO[]> =>
    backendApi.get(`${SKILL_PATH}/active${buildQuery(params)}`),
  listMine: (params: AiSkillDirectoryParams = {}): Promise<PageResult<AiSkillVO>> =>
    backendApi.get(`${SKILL_PATH}/me${buildQuery(params)}`),
  listPublic: (params: AiSkillDirectoryParams = {}): Promise<PageResult<AiSkillVO>> =>
    backendApi.get(`${SKILL_PATH}/public${buildQuery(params)}`),
  meta: (): Promise<CrudMeta> => backendApi.get(`${SKILL_PATH}/_meta`),
  create: (input: CreateAiSkillInput): Promise<AiSkillVO> => backendApi.post(SKILL_PATH, input),
  update: (id: number, input: UpdateAiSkillInput): Promise<AiSkillVO> =>
    backendApi.put(`${SKILL_PATH}/${id}`, input),
  delete: (id: number): Promise<void> => backendApi.delete(`${SKILL_PATH}/${id}`)
}

/** 当前用户可见的启用技能合集。 */
export function useActiveAiSkills(params: AiSkillsParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "active", params] as const,
    queryFn: () => skillApi.listActive(params),
    enabled,
    staleTime: 5 * 60 * 1000
  })
}

/** 兼容聊天与生成控制器的原技能查询入口。 */
export function useAiSkills(params: AiSkillsParams = {}, enabled = true) {
  return useActiveAiSkills(params, enabled)
}

/** 当前用户创建的技能目录。 */
export function useMyAiSkills(params: AiSkillDirectoryParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "me", params] as const,
    queryFn: () => skillApi.listMine(params),
    enabled
  })
}

/** 平台内置与当前组织/工作区公开的技能目录。 */
export function usePublicAiSkills(params: AiSkillDirectoryParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "public", params] as const,
    queryFn: () => skillApi.listPublic(params),
    enabled
  })
}

/** 后端声明的技能通用操作能力。 */
export function useAiSkillMeta(enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "meta"] as const,
    queryFn: skillApi.meta,
    enabled,
    staleTime: 5 * 60 * 1000
  })
}

/** 创建当前用户技能。 */
export function useCreateAiSkill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: skillApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SKILL_QUERY_KEY })
      notify.success("技能创建成功")
    }
  })
}

/** 更新当前用户拥有的技能。 */
export function useUpdateAiSkill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...input }: { id: number } & UpdateAiSkillInput) =>
      skillApi.update(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SKILL_QUERY_KEY })
      notify.success("技能更新成功")
    }
  })
}

/** 删除当前用户拥有的技能。 */
export function useDeleteAiSkill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: skillApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SKILL_QUERY_KEY })
      notify.success("技能已删除")
    }
  })
}
