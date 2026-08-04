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
