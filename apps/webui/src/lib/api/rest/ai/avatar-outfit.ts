/**
 * 助理装扮 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"
import { backendApi } from "../backend-client"
import { buildQuery } from "../crud/client"

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

export interface OutfitParams {
  type?: string
  page?: number
  size?: number
}

export interface EquipOutfitDTO {
  outfitId: number
  personaId?: number
}

export const avatarOutfitApi = {
  list: (params: OutfitParams = {}): Promise<PageResult<AvatarOutfitVO>> =>
    backendApi.get(
      `/avatar-outfits${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
    ),
  listMine: (): Promise<UserAvatarInventoryVO[]> => backendApi.get("/user-avatar-inventory/me"),
  purchase: (outfitId: number): Promise<void> =>
    backendApi.post(`/avatar-outfits/${outfitId}/purchase`),
  equip: (dto: EquipOutfitDTO): Promise<void> =>
    backendApi.post("/user-avatar-inventory/equip", dto),
  unequip: (dto: EquipOutfitDTO): Promise<void> =>
    backendApi.post("/user-avatar-inventory/unequip", dto)
}

export function useAvatarOutfits(params: OutfitParams = {}) {
  return useQuery({
    queryKey: ["avatar-outfits", params] as const,
    queryFn: () => avatarOutfitApi.list(params),
    staleTime: 5 * 60 * 1000
  })
}

export function useMyAvatarInventory() {
  return useQuery({
    queryKey: ["avatar-inventory", "me"] as const,
    queryFn: avatarOutfitApi.listMine
  })
}

export function usePurchaseOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: avatarOutfitApi.purchase,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["avatar-outfits"] })
      queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] })
      queryClient.invalidateQueries({ queryKey: ["credits"] })
      notify.success("购买成功")
    },
    onError: () => {
      // 保持原行为：购买失败由请求层统一提示。
    }
  })
}

export function useEquipOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: avatarOutfitApi.equip,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] }),
    onError: () => notify.error("装备失败，请重试")
  })
}

export function useUnequipOutfit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: avatarOutfitApi.unequip,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["avatar-inventory"] })
  })
}
