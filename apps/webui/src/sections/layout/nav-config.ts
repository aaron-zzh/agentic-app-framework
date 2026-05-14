/**
 * 导航配置——从 entityRegistry 自动生成侧边栏菜单数据
 * @author AaronZZH & Kiro
 */

import { entityRegistry } from "@/features/entity-engine"

import { paths } from "@/lib/constants/paths"

export interface NavItem {
  title: string
  path: string
  icon?: string
}

export interface NavGroup {
  group: string
  label: string
  items: NavItem[]
}

/** 从实体注册表生成导航配置 */
export function buildNavConfig(): NavGroup[] {
  const groups = entityRegistry.getByGroup()
  return Object.entries(groups).map(([group, entities]) => ({
    group,
    label: entities[0]?.groupLabel ?? group,
    items: entities.map((e) => ({
      title: e.label,
      path: paths.workspace.module(e.slug),
      icon: e.icon
    }))
  }))
}
