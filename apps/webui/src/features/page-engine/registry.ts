/**
 * Section 组件注册表——管理 Section 类型到 React 组件的映射
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * import { registerSectionType, getSectionComponent } from "@/features/page-engine"
 *
 * // 注册自定义 Section
 * registerSectionType("hero", { component: HeroSection, label: "Hero 横幅" })
 *
 * // 获取组件
 * const entry = getSectionComponent("hero")
 * ```
 */

import type { SectionRegistryEntry } from "./types"

/** Section 组件注册表 */
const sectionComponents = new Map<string, SectionRegistryEntry>()

/** 注册 Section 类型 */
export function registerSectionType(type: string, entry: SectionRegistryEntry): void {
  sectionComponents.set(type, entry)
}

/** 获取 Section 组件 */
export function getSectionComponent(type: string): SectionRegistryEntry | undefined {
  return sectionComponents.get(type)
}

/** 获取所有已注册的 Section 类型 */
export function getAllSectionTypes(): Array<{ type: string } & SectionRegistryEntry> {
  return Array.from(sectionComponents.entries()).map(([type, entry]) => ({ type, ...entry }))
}
