/**
 * 导航配置——本地静态 fallback + 后端动态菜单转换
 * @author AaronZZH & Kiro
 *
 * 优先使用 buildNavFromApi() 将后端 MenuVO[] 转为 NavGroup[]
 * API 失败时 fallback 到 buildNavConfig()（仅保留核心入口）
 */

import type { MenuVO } from "@/lib/api/rest/user/menu"
import { paths } from "@/lib/constants/paths"

export interface NavItem {
  title: string
  path: string
  icon?: string
  /** 子菜单 */
  children?: NavItem[]
  /** 允许的角色（空=所有人可见） */
  allowedRoles?: string[]
  /** 是否深度匹配子路径 */
  deepMatch?: boolean
  /** 禁用 */
  disabled?: boolean
  /** 徽标 */
  badge?: string | number
}

export interface NavGroup {
  subheader: string
  items: NavItem[]
}

/**
 * 构建最小核心导航（静态 fallback，仅 API 失败时使用）
 * 完整菜单由后端 seed 数据驱动，通过 buildNavFromApi() 渲染
 */
export function buildNavConfig(): NavGroup[] {
  return [
    {
      subheader: "概览",
      items: [{ title: "工作台", path: paths.workspace.dashboard, icon: "layout-dashboard" }]
    },
    {
      subheader: "AI 创作",
      items: [
        { title: "创作项目", path: paths.aigc.root, icon: "sparkles" },
        { title: "素材库", path: paths.aigc.assets, icon: "image" }
      ]
    },
    {
      subheader: "系统",
      items: [
        { title: "设置", path: paths.workspace.settings, icon: "settings" },
        {
          title: "演示模式",
          path: paths.admin.demo,
          icon: "flask-conical",
          allowedRoles: ["SUPER_ADMIN"]
        }
      ]
    }
  ]
}

export function buildOfficialNavConfig(): NavGroup {
  return {
    subheader: "官方服务",
    items: [
      { title: "客户门户", path: "/official/portal", icon: "globe" },
      { title: "运营管理", path: "/official/admin", icon: "shield" }
    ]
  }
}

/** 将 MenuVO 子节点递归转为 NavItem[] */
function menuChildrenToItems(children: MenuVO[] | null | undefined): NavItem[] {
  return (children ?? [])
    .filter((m) => m.visible && m.menuType === "MENU")
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((m) => ({
      title: m.title,
      path: m.path ?? "#",
      icon: m.icon ?? undefined,
      deepMatch: (m.children ?? []).filter((c) => c.menuType === "MENU").length > 0,
      children: (m.children ?? []).length > 0 ? menuChildrenToItems(m.children) : undefined
    }))
}

/**
 * 将后端 MenuVO[] 树转换为 NavGroup[] 格式
 * - menuType='GROUP' → NavGroup（subheader = title）
 * - menuType='MENU' → NavItem（title/path/icon）
 * - menuType='BUTTON' → 忽略（按钮权限不渲染为菜单）
 */
export function buildNavFromApi(menus: MenuVO[]): NavGroup[] {
  return menus
    .filter((m) => m.visible)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((m) => {
      if (m.menuType === "GROUP") {
        return {
          subheader: m.title,
          items: menuChildrenToItems(m.children)
        }
      }
      // 顶层 MENU 项包裹为匿名分组
      return {
        subheader: "",
        items: [
          {
            title: m.title,
            path: m.path ?? "#",
            icon: m.icon ?? undefined,
            deepMatch: true,
            children: (m.children ?? []).length > 0 ? menuChildrenToItems(m.children) : undefined
          }
        ]
      }
    })
    .filter((g) => g.items.length > 0)
}
