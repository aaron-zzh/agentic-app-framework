/**
 * 菜单相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type MenuCreateDTO, type MenuUpdateDTO, menuApi } from "@/lib/api/menu"

const KEYS = {
  all: ["menus"] as const,
  user: ["menus", "user"] as const,
  admin: ["menus", "admin"] as const
}

/** 当前用户可见菜单树（侧边栏用，5 分钟缓存） */
export function useUserMenus() {
  return useQuery({
    queryKey: KEYS.user,
    queryFn: () => menuApi.getUserMenus(),
    staleTime: 5 * 60 * 1000
  })
}

/** 全部菜单树（管理页面用） */
export function useAllMenus() {
  return useQuery({
    queryKey: KEYS.admin,
    queryFn: () => menuApi.getAllMenus()
  })
}

/** 创建菜单 */
export function useCreateMenu() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: MenuCreateDTO) => menuApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 更新菜单 */
export function useUpdateMenu() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: MenuUpdateDTO) => menuApi.update(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 删除菜单 */
export function useDeleteMenu() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => menuApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
