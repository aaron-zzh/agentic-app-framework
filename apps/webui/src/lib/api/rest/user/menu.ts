import { backendApi } from "../backend-client"

/**
 * 菜单 API——系统菜单 CRUD
 * @author AaronZZH & Kiro
 */

export interface MenuVO {
  id: number
  parentId: number | null
  title: string
  path: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  menuType: "GROUP" | "MENU" | "BUTTON"
  permissionCode: string | null
  children: MenuVO[]
}

export interface MenuCreateDTO {
  title: string
  parentId: number | null
  path?: string | null
  icon?: string | null
  menuType: "GROUP" | "MENU" | "BUTTON"
  sortOrder?: number
  visible?: boolean
  permissionCode?: string | null
}

export interface MenuUpdateDTO extends Partial<MenuCreateDTO> {
  id: number
}

export const menuApi = {
  /** 获取当前用户可见菜单树 */
  getUserMenus: () => backendApi.get<MenuVO[]>("/system/menus/my-tree"),
  /** 获取全部菜单树（管理用） */
  getAllMenus: () => backendApi.get<MenuVO[]>("/system/menus/tree"),
  /** 创建菜单 */
  create: (data: MenuCreateDTO) => backendApi.post<MenuVO>("/system/menus", data),
  /** 更新菜单 */
  update: (data: MenuUpdateDTO) => backendApi.put<MenuVO>(`/system/menus/${data.id}`, data),
  /** 删除菜单 */
  delete: (id: number) => backendApi.delete<void>(`/system/menus/${id}`)
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useAuthStore } from "@/lib/store/auth-store"

const KEYS = {
  all: ["menus"] as const,
  user: ["menus", "user"] as const,
  admin: ["menus", "admin"] as const
}

/** 当前用户可见菜单树（侧边栏用，5 分钟缓存） */
export function useUserMenus() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: KEYS.user,
    queryFn: () => menuApi.getUserMenus(),
    staleTime: 5 * 60 * 1000,
    enabled: isAuthenticated
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
