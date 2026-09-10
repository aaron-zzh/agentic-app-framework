/**
 * 用户管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { buildApiUrl } from "../../config"
import { backendApi } from "../backend-client"
import type { SubscriptionVO } from "../billing/plans"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  createTime: string
  updateTime: string
}

export interface UserListParams extends ListParams {
  username?: string
  nickname?: string
  status?: number
}

export interface ImportResult {
  successCount: number
  failureCount: number
  failureMessages: string[]
}

export const adminUserApi = {
  list: (params: UserListParams = {}) =>
    backendApi.get<PageResult<UserVO>>(`/system/users${buildQuery(params)}`),

  import: (file: File, updateSupport = false) => {
    const form = new FormData()
    form.append("file", file)
    form.append("updateSupport", String(updateSupport))
    return backendApi.post<ImportResult>("/system/users/import", form, {
      headers: { "Content-Type": undefined }
    })
  },

  downloadTemplate: () => {
    window.open(buildApiUrl("/system/users/import/template"), "_blank")
  },

  resetPassword: (id: number, password: string) =>
    backendApi.post<void>(`/system/users/${id}/password/reset`, { password }),

  getSubscription: (userId: number) =>
    backendApi.get<SubscriptionVO | null>(`/billing/subscriptions/admin/users/${userId}`),

  activateSubscription: (userId: number, skuCode: string) =>
    backendApi.post<SubscriptionVO>(`/billing/subscriptions/admin/users/${userId}`, { skuCode })
}

const KEYS = {
  list: (params: UserListParams) => ["admin", "users", "list", params] as const,
  subscription: (userId: number | null) => ["admin", "users", userId, "subscription"] as const
}

export function useAdminUserList(params: UserListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => adminUserApi.list(params)
  })
}

export function useAdminUserSubscription(userId: number | null) {
  return useQuery({
    queryKey: KEYS.subscription(userId),
    queryFn: () => adminUserApi.getSubscription(userId as number),
    enabled: userId !== null
  })
}

export function useAdminActivateSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, skuCode }: { userId: number; skuCode: string }) =>
      adminUserApi.activateSubscription(userId, skuCode),
    onSuccess: (_subscription, variables) =>
      queryClient.invalidateQueries({ queryKey: KEYS.subscription(variables.userId) })
  })
}
