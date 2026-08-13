/**
 * AIGC 品牌/IP 稳定身份与版本 API。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import type { AigcPageParams } from "./configuration"

export type AigcBrandProfileKind = "enterprise" | "sub_brand" | "product_line" | "personal_ip"

export interface AigcBrandProfile {
  id: number
  version: number
  name: string
  kind: AigcBrandProfileKind
  industry?: string
  currentVersionId?: number
  status: string
  createTime: string
  updateTime: string
}

export interface AigcBrandProfileVersion {
  id: number
  version: number
  brandProfileId: number
  versionNo: number
  positioning?: string
  audience?: string
  toneOfVoice?: string
  visualStyle?: string
  disclaimer?: string
  forbiddenItems?: string
  rules?: Record<string, unknown>
  status: string
  mediaVersionIds: number[]
  documentVersionIds: number[]
  createTime: string
}

export interface AigcBrandProfileSelection {
  profileId: number
  versionId: number
  name: string
  systemPrompt: string
}

export function createAigcBrandSystemPrompt(
  profile: AigcBrandProfile,
  version: AigcBrandProfileVersion
): string {
  const rules: [string, string | undefined][] = [
    ["品牌定位", version.positioning],
    ["目标受众", version.audience],
    ["表达语气", version.toneOfVoice],
    ["视觉风格", version.visualStyle],
    ["必要声明", version.disclaimer],
    ["禁用项", version.forbiddenItems]
  ]
  const populatedRules = rules.filter((entry): entry is [string, string] =>
    Boolean(entry[1]?.trim())
  )
  const serializedRules = version.rules ? JSON.stringify(version.rules) : undefined

  return [
    `请严格遵循已发布的品牌资料「${profile.name}」第 ${version.versionNo} 版：`,
    ...populatedRules.map(([label, value]) => `- ${label}：${value.trim()}`),
    ...(serializedRules && serializedRules !== "{}" ? [`- 其他品牌规则：${serializedRules}`] : [])
  ].join("\n")
}

export function mergeAigcSystemPrompts(
  ...prompts: Array<string | null | undefined>
): string | undefined {
  const resolved = prompts.filter((prompt): prompt is string => Boolean(prompt?.trim()))
  return resolved.length > 0 ? resolved.join("\n\n") : undefined
}

export interface AigcBrandProfileCreateInput {
  name: string
  kind: AigcBrandProfileKind
  industry?: string
  status: string
}

export interface AigcBrandProfileUpdateInput {
  name?: string
  kind?: AigcBrandProfileKind
  industry?: string
  currentVersionId?: number | null
  status?: string
  expectedVersion: number
}

export interface AigcBrandProfileVersionCreateInput {
  expectedProfileVersion: number
  positioning?: string
  audience?: string
  toneOfVoice?: string
  visualStyle?: string
  disclaimer?: string
  forbiddenItems?: string
  rules?: Record<string, unknown>
  mediaVersionIds?: number[]
  documentVersionIds?: number[]
}

export interface AigcBrandProfileVersionPublishInput {
  profileId: number
  versionId: number
  expectedProfileVersion: number
  expectedVersion: number
}

export const aigcBrandApi = {
  profiles: (params: AigcPageParams = {}) =>
    backendApi.get<PageResult<AigcBrandProfile>>("/aigc/brand-profiles", {
      params: { pageNo: 1, pageSize: 200, ...params }
    }),
  profile: (id: number) => backendApi.get<AigcBrandProfile>(`/aigc/brand-profiles/${id}`),
  versions: (id: number) =>
    backendApi.get<AigcBrandProfileVersion[]>(`/aigc/brand-profiles/${id}/versions`),
  create: (data: AigcBrandProfileCreateInput) =>
    backendApi.post<AigcBrandProfile>("/aigc/brand-profiles", data),
  update: (id: number, data: AigcBrandProfileUpdateInput) =>
    backendApi.put<AigcBrandProfile>(`/aigc/brand-profiles/${id}`, data),
  createVersion: (profileId: number, data: AigcBrandProfileVersionCreateInput) =>
    backendApi.post<AigcBrandProfileVersion>(`/aigc/brand-profiles/${profileId}/versions`, data),
  publishVersion: (input: AigcBrandProfileVersionPublishInput) =>
    backendApi.post<AigcBrandProfileVersion>(
      `/aigc/brand-profiles/${input.profileId}/versions/${input.versionId}/publish`,
      {
        expectedProfileVersion: input.expectedProfileVersion,
        expectedVersion: input.expectedVersion
      }
    ),
  delete: (id: number) => backendApi.delete<void>(`/aigc/brand-profiles/${id}`)
}

export const aigcBrandKeys = {
  all: ["aigc.brand"] as const,
  profiles: (params: AigcPageParams) => ["aigc.brand", "profiles", params] as const,
  versions: (id: number) => ["aigc.brand", "profiles", id, "versions"] as const
}

export function useAigcBrandProfiles(params: AigcPageParams = {}) {
  return useQuery({
    queryKey: aigcBrandKeys.profiles(params),
    queryFn: () => aigcBrandApi.profiles(params)
  })
}

export function useAigcBrandVersions(id: number | null) {
  return useQuery({
    queryKey: aigcBrandKeys.versions(id ?? 0),
    queryFn: () => aigcBrandApi.versions(id as number),
    enabled: id !== null
  })
}

export function useCreateAigcBrandProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcBrandApi.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcBrandKeys.all })
  })
}

export function useUpdateAigcBrandProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: AigcBrandProfileUpdateInput }) =>
      aigcBrandApi.update(id, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcBrandKeys.all })
  })
}

export function useDeleteAigcBrandProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcBrandApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcBrandKeys.all })
  })
}

export function useCreateAigcBrandVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      profileId,
      data
    }: {
      profileId: number
      data: AigcBrandProfileVersionCreateInput
    }) => aigcBrandApi.createVersion(profileId, data),
    onSuccess: (_version, input) => {
      queryClient.invalidateQueries({ queryKey: aigcBrandKeys.versions(input.profileId) })
      queryClient.invalidateQueries({ queryKey: aigcBrandKeys.all })
    }
  })
}

export function usePublishAigcBrandVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcBrandApi.publishVersion,
    onSuccess: (_version, input) => {
      queryClient.invalidateQueries({ queryKey: aigcBrandKeys.versions(input.profileId) })
      queryClient.invalidateQueries({ queryKey: aigcBrandKeys.all })
    }
  })
}
