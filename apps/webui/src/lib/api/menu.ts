/**
 * 菜单 API——系统菜单 CRUD
 * @author AaronZZH & Kiro
 */

import { request } from "./client"

export interface MenuVO {
  id: number
  parentId: number | null
  title: string
  path: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  menuType: "GROUP" | "MENU" | "BUTTON"
  permission: string | null
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
  permission?: string | null
}

export interface MenuUpdateDTO extends Partial<MenuCreateDTO> {
  id: number
}

export const menuApi = {
  /** 获取当前用户可见菜单树 */
  getUserMenus: () => request<MenuVO[]>("/system/menus"),
  /** 获取全部菜单树（管理用） */
  getAllMenus: () => request<MenuVO[]>("/system/menus/all"),
  /** 创建菜单 */
  create: (data: MenuCreateDTO) =>
    request<MenuVO>("/system/menus", { method: "POST", body: JSON.stringify(data) }),
  /** 更新菜单 */
  update: (data: MenuUpdateDTO) =>
    request<MenuVO>(`/system/menus/${data.id}`, { method: "PUT", body: JSON.stringify(data) }),
  /** 删除菜单 */
  delete: (id: number) => request<void>(`/system/menus/${id}`, { method: "DELETE" })
}
