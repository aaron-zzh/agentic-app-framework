/**
 * 助理装扮 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery } from "@/lib/api/rest/crud/client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

export interface AvatarOutfitVO {
  id: number
  code: string
  name: string
  type: string
  assetUrl: string
  thumbnailUrl?: string
  rarity: string
  unlockCondition: string
  entitlementCode?: string
  price?: number
  sortOrder: number
  owned?: boolean
  equipped?: boolean
}

export interface UserAvatarInventoryVO {
  id: number
  outfitId: number
  personaId?: number
  obtainedAt: string
  obtainedSource: string
  equipped: boolean
  outfit: AvatarOutfitVO
}

interface OutfitParams {
  type?: string
  page?: number
  size?: number
}

interface EquipDTO {
  outfitId: number
  personaId?: number
}

/** 装扮商城列表 */
export function useAvatarOutfits(params: OutfitParams = {}) {
  return useQuery({
    queryKey: ["avatar-outfits", params] as const,
    queryFn: () =>
      backendApi.get<PageResult<AvatarOutfitVO>>(
        `/avatar-outfits${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
      ),
    staleTime: 5 * 60 * 1000
  })
}

/** 我的装扮库存 */
export function useMyAvatarInventory() {
  return useQuery({
    queryKey: ["avatar-inventory", "me"] as const,
    queryFn: () => backendApi.get<UserAvatarInventoryVO[]>("/user-avatar-inventory/me")
  })
}

/** 购买装扮 */
export function usePurchaseOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (outfitId: number) => backendApi.post<void>(`/avatar-outfits/${outfitId}/purchase`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["avatar-outfits"] })
      queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] })
      queryClient.invalidateQueries({ queryKey: ["credits"] })
      notify.success("购买成功")
    },
    onError: () => {
      notify.error("购买失败，积分不足或请重试")
    }
  })
}

/** 装备装扮 */
export function useEquipOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: EquipDTO) => backendApi.post<void>("/user-avatar-inventory/equip", dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] })
    },
    onError: () => {
      notify.error("装备失败，请重试")
    }
  })
}

/** 卸下装扮 */
export function useUnequipOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: EquipDTO) => backendApi.post<void>("/user-avatar-inventory/unequip", dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] })
    }
  })
}
