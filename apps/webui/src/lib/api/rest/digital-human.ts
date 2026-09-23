/**
 * 数字人 API 客户端
 * @author Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "./backend-client"
import type {
  DigitalHuman,
  DigitalHumanListParams,
  DigitalHumanListResponse,
  PresetVoice
} from "@/features/digital-human/types"

export const digitalHumanApi = {
  /** 数字人分页列表 */
  list: (params: DigitalHumanListParams = {}) =>
    backendApi.get<DigitalHumanListResponse>("/api/digital-humans", { params }),

  /** 数字人详情 */
  get: (id: string) => backendApi.get<DigitalHuman>(`/api/digital-humans/${id}`),

  /** 更新数字人 */
  update: (id: string, data: Partial<DigitalHuman>) =>
    backendApi.put<DigitalHuman>(`/api/digital-humans/${id}`, data, { showSuccess: "保存成功" }),

  /** 删除数字人 */
  delete: (id: string) =>
    backendApi.delete<void>(`/api/digital-humans/${id}`, { showSuccess: "删除成功" }),

  /** 切换音色 */
  changeVoice: (id: string, data: { presetVoiceId: string; speechRate?: number; pitchRate?: number; volume?: number }) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/change-voice`, data, {
      showSuccess: "音色切换成功"
    }),

  /** 关联助理 */
  linkAssistant: (id: string, assistantId: string, syncModelConfig: boolean) =>
    backendApi.post<DigitalHuman>(
      `/api/digital-humans/${id}/link-assistant`,
      { assistantId, syncModelConfig },
      { showSuccess: "助理关联成功" }
    ),

  /** 取消关联助理 */
  unlinkAssistant: (id: string) =>
    backendApi.delete<DigitalHuman>(`/api/digital-humans/${id}/link-assistant`, {
      showSuccess: "已取消关联"
    }),

  /** 归档数字人 */
  archive: (id: string) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/archive`, undefined, {
      showSuccess: "已归档"
    }),

  /** 恢复数字人 */
  restore: (id: string) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/restore`, undefined, {
      showSuccess: "已恢复"
    }),

  /** 预设音色列表 */
  getPresetVoices: () => backendApi.get<PresetVoice[]>("/api/preset-voices"),

  /** 所有预设音色（含停用） */
  getAllPresetVoices: () => backendApi.get<PresetVoice[]>("/api/preset-voices/all")
}

const KEYS = {
  list: (params: DigitalHumanListParams) => ["digital-humans", "list", params] as const,
  detail: (id: string) => ["digital-human", id] as const,
  presetVoices: () => ["preset-voices"] as const
}

/** 数字人分页列表 */
export function useDigitalHumans(params: DigitalHumanListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => digitalHumanApi.list(params),
    staleTime: 30_000
  })
}

/** 数字人详情 */
export function useDigitalHuman(id: string | undefined) {
  return useQuery({
    queryKey: KEYS.detail(id!),
    queryFn: () => digitalHumanApi.get(id!),
    enabled: !!id
  })
}

/** 更新数字人 */
export function useUpdateDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<DigitalHuman> }) =>
      digitalHumanApi.update(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(variables.id) })
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 删除数字人 */
export function useDeleteDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => digitalHumanApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 切换音色 */
export function useChangeVoice() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof digitalHumanApi.changeVoice>[1] }) =>
      digitalHumanApi.changeVoice(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(variables.id) })
    }
  })
}

/** 关联助理 */
export function useLinkAssistant() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, assistantId, syncModelConfig }: { id: string; assistantId: string; syncModelConfig: boolean }) =>
      digitalHumanApi.linkAssistant(id, assistantId, syncModelConfig),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(variables.id) })
    }
  })
}

/** 取消关联助理 */
export function useUnlinkAssistant() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => digitalHumanApi.unlinkAssistant(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
    }
  })
}

/** 归档数字人 */
export function useArchiveDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => digitalHumanApi.archive(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 恢复数字人 */
export function useRestoreDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => digitalHumanApi.restore(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 预设音色列表 */
export function usePresetVoices() {
  return useQuery({
    queryKey: KEYS.presetVoices(),
    queryFn: () => digitalHumanApi.getPresetVoices(),
    staleTime: 5 * 60 * 1000
  })
}
