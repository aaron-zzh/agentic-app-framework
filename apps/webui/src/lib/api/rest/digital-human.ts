/**
 * 数字人 API 客户端
 * @author Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type {
  ChangeVoiceInput,
  DigitalHuman,
  DigitalHumanId,
  DigitalHumanListParams,
  DigitalHumanUpdateInput,
  PresetVoice
} from "@/features/digital-human/types"
import type { PageResult } from "../types"
import { backendApi } from "./backend-client"

/** 预设音色数量很少，一次取完（后端单页上限 200） */
const PRESET_VOICE_PAGE_SIZE = 200

export const digitalHumanApi = {
  /** 数字人分页列表 */
  list: (params: DigitalHumanListParams = {}) =>
    backendApi.get<PageResult<DigitalHuman>>("/api/digital-humans", { params }),

  /** 数字人详情 */
  get: (id: DigitalHumanId) => backendApi.get<DigitalHuman>(`/api/digital-humans/${id}`),

  /** 更新数字人（Patch，需携带 expectedVersion） */
  update: (id: DigitalHumanId, data: DigitalHumanUpdateInput) =>
    backendApi.put<DigitalHuman>(`/api/digital-humans/${id}`, data, { showSuccess: "保存成功" }),

  /** 删除数字人 */
  delete: (id: DigitalHumanId) =>
    backendApi.delete<void>(`/api/digital-humans/${id}`, { showSuccess: "删除成功" }),

  /** 切换音色 */
  changeVoice: (id: DigitalHumanId, data: ChangeVoiceInput) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/change-voice`, data, {
      showSuccess: "音色切换成功"
    }),

  /** 关联助理 */
  linkAssistant: (id: DigitalHumanId, assistantId: DigitalHumanId, syncModelConfig: boolean) =>
    backendApi.post<DigitalHuman>(
      `/api/digital-humans/${id}/link-assistant`,
      { assistantId, syncModelConfig },
      { showSuccess: "助理关联成功" }
    ),

  /** 取消关联助理 */
  unlinkAssistant: (id: DigitalHumanId) =>
    backendApi.delete<DigitalHuman>(`/api/digital-humans/${id}/link-assistant`, {
      showSuccess: "已取消关联"
    }),

  /** 归档数字人 */
  archive: (id: DigitalHumanId) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/archive`, undefined, {
      showSuccess: "已归档"
    }),

  /** 恢复数字人 */
  restore: (id: DigitalHumanId) =>
    backendApi.post<DigitalHuman>(`/api/digital-humans/${id}/restore`, undefined, {
      showSuccess: "已恢复"
    }),

  /** 启用的预设音色（按显示顺序） */
  getPresetVoices: () =>
    backendApi.get<PageResult<PresetVoice>>("/api/preset-voices", {
      params: { pageNo: 1, pageSize: PRESET_VOICE_PAGE_SIZE, isActive: true }
    })
}

const KEYS = {
  list: (params: DigitalHumanListParams) => ["digital-humans", "list", params] as const,
  detail: (id: DigitalHumanId) => ["digital-human", String(id)] as const,
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
export function useDigitalHuman(id: DigitalHumanId | undefined) {
  return useQuery({
    queryKey: KEYS.detail(id ?? ""),
    queryFn: () => {
      if (id === undefined || id === "") throw new Error("数字人 ID 不能为空")
      return digitalHumanApi.get(id)
    },
    enabled: id !== undefined && id !== ""
  })
}

/** 更新数字人 */
export function useUpdateDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: DigitalHumanId; data: DigitalHumanUpdateInput }) =>
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
    mutationFn: (id: DigitalHumanId) => digitalHumanApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 切换音色 */
export function useChangeVoice() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: DigitalHumanId; data: ChangeVoiceInput }) =>
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
    mutationFn: ({
      id,
      assistantId,
      syncModelConfig
    }: {
      id: DigitalHumanId
      assistantId: DigitalHumanId
      syncModelConfig: boolean
    }) => digitalHumanApi.linkAssistant(id, assistantId, syncModelConfig),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(variables.id) })
    }
  })
}

/** 取消关联助理 */
export function useUnlinkAssistant() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: DigitalHumanId) => digitalHumanApi.unlinkAssistant(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
    }
  })
}

/** 归档数字人 */
export function useArchiveDigitalHuman() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: DigitalHumanId) => digitalHumanApi.archive(id),
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
    mutationFn: (id: DigitalHumanId) => digitalHumanApi.restore(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      queryClient.invalidateQueries({ queryKey: ["digital-humans", "list"] })
    }
  })
}

/** 启用的预设音色列表 */
export function usePresetVoices() {
  return useQuery({
    queryKey: KEYS.presetVoices(),
    queryFn: () => digitalHumanApi.getPresetVoices(),
    select: (page): PresetVoice[] => page.list,
    staleTime: 5 * 60 * 1000
  })
}
