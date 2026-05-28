/**
 * 导航配置——本地菜单数据 + entityRegistry 自动生成
 * @author AaronZZH & Kiro
 *
 * 当前：本地静态数据 + entityRegistry 合并
 * 后续：改为 GET /api/menus 从后端获取（RBAC 动态过滤）
 */

import { entityRegistry } from "@/features/entity-engine"
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
      { title: "素材库", path: paths.aigc.assets, icon: "image" },
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
 * 构建完整导航配置
 * TODO: 后续替换为 GET /api/menus，后端根据用户角色返回可见菜单
 */
export function buildNavConfig(): NavGroup[] {
  return [...STATIC_NAV, ...buildEntityNav(), ...BOTTOM_NAV]
}
