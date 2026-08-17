/**
 * AI 技能根对象、不可变版本 API 与 TanStack Query Hook。
 *
 * @example
 * const { data } = useMyAiSkills({ search: "写作", pageNo: 1, pageSize: 20 })
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

export type AiSkillVisibility = "PRIVATE" | "WORKSPACE" | "PUBLIC"
export type AiSkillVersionStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "RETIRED"
export type AiSkillToolAccessMode = "RESTRICT" | "INHERIT"

export interface AiSkillToolRequirementVO {
  id: number
  toolId: string
  toolVersion: number | null
  toolName: string
  required: boolean
  usagePurpose: string
  sortOrder: number
}

export interface AiSkillModelRequirementVO {
  id: number
  capability: string
  required: boolean
  minimumContextTokens: number | null
  rationale: string
}

export interface AiSkillVersionVO {
  id: number
  version: number
  status: AiSkillVersionStatus
  content: string
  inputSchema: string | null
  outputSchema: string | null
  outputContract: string | null
  toolAccessMode: AiSkillToolAccessMode
  toolRequirements: AiSkillToolRequirementVO[]
  modelRequirements: AiSkillModelRequirementVO[]
  changeSummary: string | null
  contentHash: string
  authoredBy: number | null
  createTime: string
}

export interface AiSkillVO {
  id: number
  version: number
  code: string
  name: string
  summary: string
  locale: string
  visibility: AiSkillVisibility
  builtIn: boolean
  currentVersionId: number | null
  sourceSkillId: number | null
  currentVersion: AiSkillVersionVO | null
  latestVersion: AiSkillVersionVO | null
  ownerId: number | null
  ownedByCurrentUser: boolean
  createTime: string
  updateTime: string
}

export interface AiSkillsParams extends Record<string, SkillQueryParamValue> {
  locale?: string
  publishedOnly?: boolean
}

export interface AiSkillDirectoryParams extends AiSkillsParams {
  search?: string
  visibility?: AiSkillVisibility
  builtIn?: boolean
  pageNo?: number
  pageSize?: number
}

export interface AiSkillToolRequirementInput {
  toolId: string
  toolVersion?: number
  toolName: string
  required?: boolean
  usagePurpose: string
  sortOrder?: number
}

export interface AiSkillModelRequirementInput {
  capability: string
  required?: boolean
  minimumContextTokens?: number
  rationale: string
}

export interface CreateAiSkillInput {
  code: string
  name: string
  summary: string
  locale: string
  visibility: AiSkillVisibility
  sourceSkillId?: number
  content: string
  inputSchema?: string
  outputSchema?: string
  outputContract?: string
  toolAccessMode: AiSkillToolAccessMode
  toolRequirements?: AiSkillToolRequirementInput[]
  modelRequirements?: AiSkillModelRequirementInput[]
  changeSummary?: string
  status: AiSkillVersionStatus
}

export type UpdateAiSkillInput = Partial<CreateAiSkillInput>

export const skillApi = {
  listVisible: (params: AiSkillsParams = {}): Promise<AiSkillVO[]> =>
    backendApi.get(`${SKILL_PATH}/visible${buildQuery(params)}`),
  listMine: (params: AiSkillDirectoryParams = {}): Promise<PageResult<AiSkillVO>> =>
    backendApi.get(`${SKILL_PATH}/me${buildQuery(params)}`),
  listPublic: (params: AiSkillDirectoryParams = {}): Promise<PageResult<AiSkillVO>> =>
    backendApi.get(`${SKILL_PATH}/public${buildQuery(params)}`),
  versions: (skillId: number): Promise<AiSkillVersionVO[]> =>
    backendApi.get(`${SKILL_PATH}/${skillId}/versions`),
  publishVersion: (skillId: number, versionId: number): Promise<AiSkillVO> =>
    backendApi.post(`${SKILL_PATH}/${skillId}/versions/${versionId}/publish`),
  meta: (): Promise<CrudMeta> => backendApi.get(`${SKILL_PATH}/_meta`),
  create: (input: CreateAiSkillInput): Promise<AiSkillVO> => backendApi.post(SKILL_PATH, input),
  update: (id: number, input: UpdateAiSkillInput): Promise<AiSkillVO> =>
    backendApi.put(`${SKILL_PATH}/${id}`, input),
  delete: (id: number): Promise<void> => backendApi.delete(`${SKILL_PATH}/${id}`)
}

/** 当前用户可见且有已发布版本的技能合集。 */
export function useAiSkills(params: AiSkillsParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "visible", params] as const,
    queryFn: () => skillApi.listVisible(params),
    enabled,
    staleTime: 5 * 60 * 1000
  })
}

/** 当前用户创建的技能目录。 */
export function useMyAiSkills(params: AiSkillDirectoryParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "me", params] as const,
    queryFn: () => skillApi.listMine(params),
    enabled
  })
}

/** 平台内置与当前组织/工作区共享的技能目录。 */
export function usePublicAiSkills(params: AiSkillDirectoryParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, "public", params] as const,
    queryFn: () => skillApi.listPublic(params),
    enabled
  })
}

/** 指定技能的不可变版本历史。 */
export function useAiSkillVersions(skillId: number | null, enabled = true) {
  return useQuery({
    queryKey: [...SKILL_QUERY_KEY, skillId ?? 0, "versions"] as const,
    queryFn: () => skillApi.versions(skillId ?? 0),
    enabled: enabled && skillId !== null
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

/** 创建技能根对象与首个不可变版本。 */
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

/** 更新技能根元数据并追加不可变版本。 */
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

/** 将已审核版本设置为当前发布版本。 */
export function usePublishAiSkillVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ skillId, versionId }: { skillId: number; versionId: number }) =>
      skillApi.publishVersion(skillId, versionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SKILL_QUERY_KEY })
      notify.success("技能版本已发布")
    }
  })
}

/** 删除当前用户拥有的技能根对象。 */
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
