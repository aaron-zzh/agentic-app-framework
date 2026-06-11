/**
 * 个人资料数据层——API + Query Options + Hooks。
 */

import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "../backend-client"

/** 个人资料 */
export interface ProfileVO {
  id: string
  email: string
  nickname: string
  avatar?: string
  phone?: string
  bio?: string
}

/** 更新个人资料请求 */
export interface ProfileUpdateReq {
  nickname?: string
  avatar?: string
  phone?: string
  bio?: string
}

/** 修改密码请求 */
export interface ChangePasswordReq {
  oldPassword: string
  newPassword: string
}

export const profileApi = {
  get: () => backendApi.get<ProfileVO>("/system/user/profile"),

  update: (data: ProfileUpdateReq) => backendApi.put<ProfileVO>("/system/user/profile", data),

  changePassword: (data: ChangePasswordReq) =>
    backendApi.put<void>("/system/user/profile/password", data)
}

export const profileQueries = {
  detail: () =>
    queryOptions({
      queryKey: ["user", "profile"] as const,
      queryFn: profileApi.get,
      staleTime: 60_000
    })
}

export function useProfile() {
  return useQuery(profileQueries.detail())
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: profileApi.update,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: profileQueries.detail().queryKey })
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: profileApi.changePassword
  })
}
