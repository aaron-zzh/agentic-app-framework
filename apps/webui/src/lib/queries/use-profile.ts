/**
 * 个人资料 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  type ChangePasswordReq,
  type ProfileUpdateReq,
  profileApi
} from "@/lib/api/profile"

const KEYS = {
  profile: ["user", "profile"] as const
}

/** 查询当前用户个人资料 */
export function useProfile() {
  return useQuery({
    queryKey: KEYS.profile,
    queryFn: profileApi.get
  })
}

/** 更新个人资料 */
export function useUpdateProfile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: ProfileUpdateReq) => profileApi.update(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.profile })
  })
}

/** 修改密码 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (data: ChangePasswordReq) => profileApi.changePassword(data)
  })
}
