/**
 * 导航配置——本地静态 fallback + 后端动态菜单转换
 * @author AaronZZH & Kiro
 *
 * 优先使用 buildNavFromApi() 将后端 MenuVO[] 转为 NavGroup[]
 * API 失败时 fallback 到 buildNavConfig()（本地静态 + entityRegistry）
 */

import { entityRegistry } from "@/features/entity-engine"
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

/** 固定菜单项（不依赖 entityRegistry） */
const STATIC_NAV: NavGroup[] = [
  {
    subheader: "概览",
    items: [{ title: "工作台", path: paths.workspace.dashboard, icon: "layout-dashboard" }]
  },
  {
    subheader: "AI 创作",
    items: [
      { title: "图像生成", path: paths.aigc.root, icon: "sparkles" },
      { title: "视频生成", path: paths.aigc.video, icon: "video" },
      { title: "3D 展示", path: "/aigc/3d", icon: "box" },
      { title: "素材库", path: paths.aigc.assets, icon: "image" }
    ]
  }
]

/** 开发工具菜单 */
const DEV_NAV: NavGroup[] = [
  {
    subheader: "开发工具",
    items: [
      { title: "文档管理", path: "/dev/docs", icon: "file-text" },
      { title: "开发日志", path: "/dev/log", icon: "scroll-text" },
      { title: "代码审查", path: "/dev/review", icon: "git-pull-request" },
      { title: "迭代统计", path: "/dev/stats", icon: "bar-chart-3" }
    ]
  }
]

/** 固定底部菜单 */
const BOTTOM_NAV: NavGroup[] = [
  {
    subheader: "系统",
    items: [
      { title: "🗑️ 回收站", path: paths.workspace.trash, icon: "trash-2" },
      {
        title: "设置",
        path: paths.workspace.settings,
        icon: "settings",
        children: [
          { title: "个人资料", path: `${paths.workspace.settings}/profile` },
          { title: "API Key", path: `${paths.workspace.settings}/api-keys` },
          { title: "团队管理", path: `${paths.workspace.settings}/team`, badge: 3 },
          {
            title: "系统配置",
            path: `${paths.workspace.settings}/system`,
            children: [
              { title: "模型管理", path: `${paths.workspace.settings}/system/model` },
              {
                title: "插件市场",
                path: `${paths.workspace.settings}/system/plugins`,
                badge: "NEW"
              }
            ]
          }
        ]
      }
    ]
  }
]

/** 从 entityRegistry 生成实体菜单组 */
function buildEntityNav(): NavGroup[] {
  const groups = entityRegistry.getByGroup()
  return Object.entries(groups).map(([group, entities]) => ({
    subheader: entities[0]?.groupLabel ?? group,
    items: entities.map((e) => ({
      title: e.label,
      path: paths.workspace.module(e.slug),
      icon: e.icon,
      deepMatch: true
    }))
  }))
}

/**
 * 构建完整导航配置（静态 fallback，API 失败时使用）
 */
export function buildNavConfig(): NavGroup[] {
  return [...STATIC_NAV, ...buildEntityNav(), ...DEV_NAV, ...BOTTOM_NAV]
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
      deepMatch: true,
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
