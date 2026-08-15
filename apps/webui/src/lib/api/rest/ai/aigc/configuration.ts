/**
 * AIGC 项目配置 API：项目类型、蓝图、渠道规格与创作片段。
 * @author AaronZZH & Kiro
 */

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { notify } from "@/lib/notification"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import type { CrudMeta } from "../../crud/client"

export type AigcConfigStatus = "draft" | "verifying" | "published" | "deprecated" | "withdrawn"
export type AigcProductionMode = "standard" | "short_drama" | "motion_comic"
export type AigcProjectTypeCode = string
export type AigcChannelCode = string

export interface AigcPageParams {
  pageNo?: number
  pageSize?: number
}

export interface AigcProjectType {
  id: number
  version: number
  code: AigcProjectTypeCode
  name: string
  icon?: string
  description?: string
  briefPlaceholder?: string
  definitionVersion?: string
  defaultChannels: AigcChannelCode[]
  defaultProductionMode?: AigcProductionMode
  quickEntry: boolean
  builtin: boolean
  sortOrder: number
  status: AigcConfigStatus
}

export interface AigcProjectBlueprint {
  id: number
  version: number
  code: string
  name: string
  projectTypeCode: AigcProjectTypeCode
  blueprintVersion: string
  productionMode: AigcProductionMode
  description?: string
  coverUrl?: string | null
  status: AigcConfigStatus
  objectSpec?: Record<string, unknown>
  relationSpec?: Record<string, unknown>
  deliverableSpec?: Record<string, unknown>
  actionKeys: string[]
  confirmationGates: string[]
  briefFields: string[]
}

export interface AigcDomainExtension {
  id: number
  version: number
  code: string
  name: string
  extensionVersion: string
  industry?: string
  region?: string
  language?: string
  status: AigcConfigStatus
  profileSchemaExt?: Record<string, unknown>
  objectDefinitions?: Record<string, unknown>
  knowledgeRequirements?: Record<string, unknown>
  ruleSets?: Record<string, unknown>
  validators: string[]
  roleRecommendations: string[]
  actionConstraints?: Record<string, unknown>
  channelOverrides?: Record<string, unknown>
  migrationDeclaration?: Record<string, unknown>
}

export interface AigcChannelSpec {
  id: number
  version: number
  code: AigcChannelCode
  name: string
  specVersion: string
  aspectRatio?: string
  width?: number
  height?: number
  maxDurationSeconds?: number
  copyStructure?: Record<string, unknown>
  requiredDisclaimers?: string
  exportFormat?: string
  sortOrder: number
  status: AigcConfigStatus
}

export interface AigcSnippet {
  id: number
  version: number
  name: string
  category?: string
  content?: string
  referenceMediaVersionIds: number[]
  variableSlots?: Record<string, unknown>
  projectTypeCode?: AigcProjectTypeCode
  brandProfileId?: number
  useCount: number
  isPublic: boolean
  builtin: boolean
  ownedByCurrentUser: boolean
}

export interface AigcSnippetInput {
  name: string
  category?: string
  content: string
  isPublic: boolean
}

export interface AigcStatusPageParams extends AigcPageParams {
  status?: AigcConfigStatus
}

export interface AigcProjectBlueprintParams extends AigcStatusPageParams {
  projectTypeCode?: AigcProjectTypeCode
  productionMode?: AigcProductionMode
}

export interface AigcSnippetParams extends AigcPageParams {
  category?: string
  projectTypeCode?: AigcProjectTypeCode
  ownerOnly?: boolean
  publicOnly?: boolean
  search?: string
  sort?: string
}

const withPage = <T extends AigcPageParams>(
  params: T
): T & { pageNo: number; pageSize: number } => ({
  pageNo: 1,
  pageSize: 200,
  ...params
})

export const aigcConfigurationApi = {
  projectTypes: (params: AigcPageParams = {}) =>
    backendApi.get<PageResult<AigcProjectType>>("/aigc/project-types", {
      params: withPage(params)
    }),
  projectBlueprints: (params: AigcProjectBlueprintParams = {}) =>
    backendApi.get<PageResult<AigcProjectBlueprint>>("/aigc/project-blueprints", {
      params: withPage(params)
    }),
  channelSpecs: (params: AigcStatusPageParams = {}) =>
    backendApi.get<PageResult<AigcChannelSpec>>("/aigc/channel-specs", {
      params: withPage(params)
    }),
  domainExtensions: (params: AigcStatusPageParams = {}) =>
    backendApi.get<PageResult<AigcDomainExtension>>("/aigc/domain-extensions", {
      params: withPage(params)
    }),
  snippets: (params: AigcSnippetParams = {}) =>
    backendApi.get<PageResult<AigcSnippet>>("/aigc/snippets", {
      params: withPage(params)
    }),
  snippetMeta: () => backendApi.get<CrudMeta>("/aigc/snippets/_meta"),
  createSnippet: (input: AigcSnippetInput) =>
    backendApi.post<AigcSnippet>("/aigc/snippets", {
      ...input,
      referenceMediaVersionIds: [],
      useCount: 0
    }),
  updateSnippet: ({ id, version, ...input }: AigcSnippetInput & { id: number; version: number }) =>
    backendApi.put<AigcSnippet>(`/aigc/snippets/${id}`, {
      ...input,
      expectedVersion: version
    }),
  deleteSnippet: (id: number) => backendApi.delete<void>(`/aigc/snippets/${id}`)
}

export const aigcConfigurationKeys = {
  all: ["aigc.configuration"] as const,
  projectTypes: (params: AigcPageParams) =>
    ["aigc.configuration", "project-types", params] as const,
  projectBlueprints: (params: AigcProjectBlueprintParams) =>
    ["aigc.configuration", "project-blueprints", params] as const,
  channelSpecs: (params: AigcStatusPageParams) =>
    ["aigc.configuration", "channel-specs", params] as const,
  domainExtensions: (params: AigcStatusPageParams) =>
    ["aigc.configuration", "domain-extensions", params] as const,
  snippets: (params: AigcSnippetParams) => ["aigc.configuration", "snippets", params] as const,
  infiniteSnippets: (params: AigcSnippetParams) =>
    ["aigc.configuration", "snippets", "infinite", params] as const,
  snippetMeta: ["aigc.configuration", "snippets", "meta"] as const
}

export function useAigcProjectTypes(params: AigcPageParams = {}) {
  return useQuery({
    queryKey: aigcConfigurationKeys.projectTypes(params),
    queryFn: () => aigcConfigurationApi.projectTypes(params)
  })
}

export function useAigcProjectBlueprints(params: AigcProjectBlueprintParams = {}, enabled = true) {
  return useQuery({
    queryKey: aigcConfigurationKeys.projectBlueprints(params),
    queryFn: () => aigcConfigurationApi.projectBlueprints(params),
    enabled
  })
}

export function useAigcChannelSpecs(params: AigcStatusPageParams = {}) {
  return useQuery({
    queryKey: aigcConfigurationKeys.channelSpecs(params),
    queryFn: () => aigcConfigurationApi.channelSpecs(params)
  })
}

export function useAigcDomainExtensions(params: AigcStatusPageParams = {}) {
  return useQuery({
    queryKey: aigcConfigurationKeys.domainExtensions(params),
    queryFn: () => aigcConfigurationApi.domainExtensions(params)
  })
}

export function useAigcSnippets(params: AigcSnippetParams = {}) {
  return useQuery({
    queryKey: aigcConfigurationKeys.snippets(params),
    queryFn: () => aigcConfigurationApi.snippets(params)
  })
}

/** 分页追加加载创作片段。 */
export function useInfiniteAigcSnippets(params: AigcSnippetParams = {}, enabled = true) {
  return useInfiniteQuery({
    queryKey: aigcConfigurationKeys.infiniteSnippets(params),
    initialPageParam: 1,
    queryFn: ({ pageParam }) =>
      aigcConfigurationApi.snippets({
        ...params,
        pageNo: pageParam,
        pageSize: 20,
        sort: params.sort ?? "id:desc"
      }),
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((total, page) => total + page.list.length, 0)
      return loaded < lastPage.total ? pages.length + 1 : undefined
    },
    enabled
  })
}

/** 后端声明的片段操作能力。 */
export function useAigcSnippetMeta() {
  return useQuery({
    queryKey: aigcConfigurationKeys.snippetMeta,
    queryFn: aigcConfigurationApi.snippetMeta,
    staleTime: 5 * 60 * 1000
  })
}

export function useCreateAigcSnippet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcConfigurationApi.createSnippet,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: aigcConfigurationKeys.all })
      notify.success("片段已创建")
    }
  })
}

export function useUpdateAigcSnippet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcConfigurationApi.updateSnippet,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: aigcConfigurationKeys.all })
      notify.success("片段已更新")
    }
  })
}

export function useDeleteAigcSnippet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcConfigurationApi.deleteSnippet,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: aigcConfigurationKeys.all })
      notify.success("片段已删除")
    }
  })
}
