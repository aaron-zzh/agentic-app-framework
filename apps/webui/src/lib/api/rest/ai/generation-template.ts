/**
 * 统一提示词资产 API 与 TanStack Query Hooks。
 *
 * @example
 * const { data } = useSystemPromptTemplates({ type: "IMAGE_GEN", scope: "GENERATION" })
 * @author AaronZZH & Kiro
 */

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

import { backendApi } from "../backend-client"
import { buildQuery, type CrudMeta } from "../crud/client"

const PROMPT_TEMPLATE_PATH = "/aigc/prompt-templates"
const PROMPT_TEMPLATE_KEY = ["prompt-template-assets"] as const

export type PromptTemplateVisibility = "ENGINE" | "SYSTEM" | "PRIVATE" | "PUBLIC"

export interface PromptTemplateAssetVO {
  id: number
  version: number
  type: string
  name: string
  category: string | null
  prompt: string
  negativePrompt: string | null
  model: string | null
  width: number | null
  height: number | null
  steps: number | null
  seed: number | null
  isPublic: boolean
  visibility: PromptTemplateVisibility
  usageCount: number
  scope: string
  description: string | null
  variables: string[]
  createTime: string
  updateTime: string
}

type QueryParamValue = string | number | boolean | string[] | undefined

export interface PromptTemplateDirectoryParams extends Record<string, QueryParamValue> {
  type?: string
  category?: string
  scope?: string
  search?: string
  page?: number
  size?: number
}

export type SystemPromptTemplateParams = PromptTemplateDirectoryParams
export type PublicPromptTemplateParams = PromptTemplateDirectoryParams

export interface MyPromptTemplateParams extends Record<string, QueryParamValue> {
  type?: string
  category?: string
  scope?: string
  search?: string
  pageNo?: number
  pageSize?: number
}

export interface CreatePromptTemplateInput {
  name: string
  type?: string
  category?: string
  prompt: string
  negativePrompt?: string
  model?: string
  width?: number
  height?: number
  steps?: number
  seed?: number
  isPublic?: boolean
  scope?: string
  description?: string
  variables?: string[]
}

export type UpdatePromptTemplateInput = Partial<CreatePromptTemplateInput>

export interface UsePromptTemplateInput {
  id: number
  variables: Record<string, string>
}

export interface PromptTemplateUseResult {
  id: number
  prompt: string
  negativePrompt: string | null
  usageCount: number
}

export interface CopyPromptTemplateInput {
  id: number
  name?: string
}

export const promptTemplateAssetApi = {
  listSystem: (
    params: SystemPromptTemplateParams = {}
  ): Promise<PageResult<PromptTemplateAssetVO>> =>
    backendApi.get(`${PROMPT_TEMPLATE_PATH}/system${buildQuery(params)}`),
  listPublic: (
    params: PublicPromptTemplateParams = {}
  ): Promise<PageResult<PromptTemplateAssetVO>> =>
    backendApi.get(`${PROMPT_TEMPLATE_PATH}/public${buildQuery(params)}`),
  listMine: (params: MyPromptTemplateParams = {}): Promise<PageResult<PromptTemplateAssetVO>> =>
    backendApi.get(`${PROMPT_TEMPLATE_PATH}/me${buildQuery(params)}`),
  meta: (): Promise<CrudMeta> => backendApi.get(`${PROMPT_TEMPLATE_PATH}/_meta`),
  create: (input: CreatePromptTemplateInput): Promise<PromptTemplateAssetVO> =>
    backendApi.post(PROMPT_TEMPLATE_PATH, input),
  update: (id: number, input: UpdatePromptTemplateInput): Promise<PromptTemplateAssetVO> =>
    backendApi.put(`${PROMPT_TEMPLATE_PATH}/${id}`, input),
  delete: (id: number): Promise<void> => backendApi.delete(`${PROMPT_TEMPLATE_PATH}/${id}`),
  use: ({ id, variables }: UsePromptTemplateInput): Promise<PromptTemplateUseResult> =>
    backendApi.post(`${PROMPT_TEMPLATE_PATH}/${id}/use`, { variables }),
  copy: ({ id, name }: CopyPromptTemplateInput): Promise<PromptTemplateAssetVO> =>
    backendApi.post(`${PROMPT_TEMPLATE_PATH}/${id}/copy`, name ? { name } : {})
}

/** Studio 全局系统提示词资产。 */
export function useSystemPromptTemplates(params: SystemPromptTemplateParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "system", params] as const,
    queryFn: () => promptTemplateAssetApi.listSystem(params),
    enabled
  })
}

/** 平台内置与当前组织/工作区公开的统一提示词目录。 */
export function usePublicPromptTemplates(params: PublicPromptTemplateParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "public", params] as const,
    queryFn: () => promptTemplateAssetApi.listPublic(params),
    enabled
  })
}

/** 当前用户创建的提示词资产。 */
export function useMyPromptTemplates(params: MyPromptTemplateParams = {}, enabled = true) {
  return useQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "me", params] as const,
    queryFn: () => promptTemplateAssetApi.listMine(params),
    enabled
  })
}

/** Studio 系统提示词滚动分页。 */
export function useInfiniteSystemPromptTemplates(
  params: SystemPromptTemplateParams = {},
  enabled = true
) {
  return useInfiniteQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "system", "infinite", params] as const,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      promptTemplateAssetApi.listSystem({ ...params, page: pageParam, size: 20 }),
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((total, page) => total + page.list.length, 0)
      return loaded < lastPage.total ? pages.length : undefined
    },
    enabled
  })
}

/** 统一公共提示词滚动分页。 */
export function useInfinitePublicPromptTemplates(
  params: PublicPromptTemplateParams = {},
  enabled = true
) {
  return useInfiniteQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "public", "infinite", params] as const,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      promptTemplateAssetApi.listPublic({ ...params, page: pageParam, size: 20 }),
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((total, page) => total + page.list.length, 0)
      return loaded < lastPage.total ? pages.length : undefined
    },
    enabled
  })
}

/** 我的提示词滚动分页。 */
export function useInfiniteMyPromptTemplates(params: MyPromptTemplateParams = {}, enabled = true) {
  return useInfiniteQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "me", "infinite", params] as const,
    initialPageParam: 1,
    queryFn: ({ pageParam }) =>
      promptTemplateAssetApi.listMine({ ...params, pageNo: pageParam, pageSize: 20 }),
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((total, page) => total + page.list.length, 0)
      return loaded < lastPage.total ? pages.length + 1 : undefined
    },
    enabled
  })
}

/** 后端声明的提示词资产通用操作能力。 */
export function usePromptTemplateMeta(enabled = true) {
  return useQuery({
    queryKey: [...PROMPT_TEMPLATE_KEY, "meta"] as const,
    queryFn: promptTemplateAssetApi.meta,
    enabled,
    staleTime: 5 * 60 * 1000
  })
}

/** 服务端安全编译并使用可见提示词。 */
export function usePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: promptTemplateAssetApi.use,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_KEY })
  })
}

/** 复制可见提示词为当前用户的私有资产。 */
export function useCopyPromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: promptTemplateAssetApi.copy,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_KEY })
      notify.success("已复制到我的提示词")
    }
  })
}

/** 创建提示词资产。 */
export function useCreatePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: promptTemplateAssetApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_KEY })
      notify.success("创建成功")
    }
  })
}

/** 更新本人创建的提示词资产。 */
export function useUpdatePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...input }: { id: number } & UpdatePromptTemplateInput) =>
      promptTemplateAssetApi.update(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_KEY })
      notify.success("更新成功")
    }
  })
}

/** 删除本人创建的提示词资产。 */
export function useDeletePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: promptTemplateAssetApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_KEY })
  })
}
