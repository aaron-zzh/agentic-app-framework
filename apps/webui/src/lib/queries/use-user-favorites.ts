/**
 * 用户收藏夹 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery } from "@/lib/api/rest/crud/client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

const API_PATH = "/user-favorites"

export interface UserFavoriteVO {
  id: number
  targetType: string
  targetId: number
  note?: string
  sortOrder: number
  createTime: string
  targetTitle?: string
  targetCoverUrl?: string
}

interface FavoritesParams {
  targetType?: string
  page?: number
  size?: number
}

interface AddFavoriteDTO {
  targetType: string
  targetId: number
  note?: string
}

const FAVORITES_KEY = ["user-favorites"] as const

/** 我的收藏列表 */
export function useUserFavorites(params: FavoritesParams = {}) {
  return useQuery({
    queryKey: [...FAVORITES_KEY, params] as const,
    queryFn: () =>
      backendApi.get<PageResult<UserFavoriteVO>>(
        `${API_PATH}${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
      )
  })
}

/** 添加收藏 */
export function useAddFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: AddFavoriteDTO) => backendApi.post<UserFavoriteVO>(API_PATH, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
      notify.success("已收藏")
    },
    onError: () => {
      notify.error("收藏失败")
    }
  })
}

/** 删除收藏（按 id） */
export function useRemoveFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => backendApi.delete<void>(`${API_PATH}/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
    }
  })
}

/** Toggle 收藏（按 targetType+targetId，已收藏则删除，未收藏则添加） */
export function useToggleFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: AddFavoriteDTO) => {
      // 先尝试 by-target 删除，后端返回 null 表示未收藏，此时新增
      try {
        await backendApi.delete<void>(
          `${API_PATH}/by-target?targetType=${dto.targetType}&targetId=${dto.targetId}`
        )
        return { action: "removed" as const }
      } catch {
        // 未收藏，则添加
        await backendApi.post<UserFavoriteVO>(API_PATH, dto)
        return { action: "added" as const }
      }
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
      if (result.action === "added") notify.success("已收藏")
    }
  })
}
