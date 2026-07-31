/**
 * 用户收藏 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"
import { backendApi } from "../backend-client"
import { buildQuery } from "../crud/client"

const API_PATH = "/user-favorites"
const FAVORITES_KEY = ["user-favorites"] as const

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

export interface FavoritesParams {
  targetType?: string
  pageNo?: number
  pageSize?: number
}

export interface AddFavoriteDTO {
  targetType: string
  targetId: number
  note?: string
}

export const favoriteApi = {
  list: (params: FavoritesParams = {}): Promise<PageResult<UserFavoriteVO>> =>
    backendApi.get(
      `${API_PATH}${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
    ),
  add: (dto: AddFavoriteDTO): Promise<UserFavoriteVO> => backendApi.post(API_PATH, dto),
  remove: (id: number): Promise<void> => backendApi.delete(`${API_PATH}/${id}`),
  removeByTarget: (dto: AddFavoriteDTO): Promise<void> =>
    backendApi.delete(`${API_PATH}/by-target?targetType=${dto.targetType}&targetId=${dto.targetId}`)
}

export function useUserFavorites(params: FavoritesParams = {}) {
  return useQuery({
    queryKey: [...FAVORITES_KEY, params] as const,
    queryFn: () => favoriteApi.list(params)
  })
}

export function useAddFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: favoriteApi.add,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
      notify.success("已收藏")
    },
    onError: () => notify.error("收藏失败")
  })
}

export function useRemoveFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: favoriteApi.remove,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
  })
}

export function useToggleFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: AddFavoriteDTO) => {
      const page = await favoriteApi.list({
        targetType: dto.targetType,
        pageNo: 1,
        pageSize: 200
      })
      const existing = page.list.find((favorite) => favorite.targetId === dto.targetId)
      if (existing) {
        await favoriteApi.remove(existing.id)
        return { action: "removed" as const }
      }
      await favoriteApi.add(dto)
      return { action: "added" as const }
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: FAVORITES_KEY })
      if (result.action === "added") notify.success("已收藏")
    }
  })
}
