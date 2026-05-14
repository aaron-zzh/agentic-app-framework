/**
 * Mixin 合并与继承解析
 * @author AaronZZH & Kiro
 */

import type { EntityDef, FieldDef } from "../types"

import { builtinMixins, type MixinDef } from "./mixins"

/**
 * 合并 Mixin 字段到 EntityDef
 *
 * 规则：
 * - Mixin 字段追加到 fields 末尾
 * - 同名字段自身覆盖 Mixin（自身优先）
 * - 多个 Mixin 按声明顺序合并
 *
 * ```ts
 * const resolved = resolveMixins(entity)
 * // entity.mixins: ["timestamp", "audit"]
 * // → fields 末尾追加 createTime/updateTime/createBy/updateBy
 * ```
 */
export function resolveMixins(
  def: EntityDef,
  mixinRegistry: Record<string, MixinDef> = builtinMixins
): EntityDef {
  if (!def.mixins?.length) return def

  const mixinFields: FieldDef[] = []
  for (const mixinName of def.mixins) {
    const mixin = mixinRegistry[mixinName]
    if (!mixin) {
      throw new Error(`Mixin "${mixinName}" not found for entity "${def.slug}"`)
    }
    mixinFields.push(...mixin.fields)
  }

  // 自身字段名集合（用于去重）
  const ownFieldNames = new Set(
    def.fields.filter((f) => "name" in f).map((f) => (f as { name: string }).name)
  )

  // 只追加自身没有的 Mixin 字段
  const mergedFields = [
    ...def.fields,
    ...mixinFields.filter((f) => "name" in f && !ownFieldNames.has((f as { name: string }).name)),
  ]

  return { ...def, fields: mergedFields }
}

/**
 * 解析继承：从父实体继承配置
 *
 * 规则：
 * - 子实体字段覆盖父实体同名字段
 * - 父实体独有字段追加到子实体前面
 * - listView 浅合并（子覆盖父）
 * - formView/kanbanView/access 子无则继承父
 *
 * ```ts
 * const article: EntityDef = { extends: "base-doc", fields: [...] }
 * const resolved = resolveExtends(article, (slug) => registry.get(slug))
 * ```
 */
export function resolveExtends(
  def: EntityDef,
  getParent: (slug: string) => EntityDef | undefined
): EntityDef {
  if (!def.extends) return def

  const parent = getParent(def.extends)
  if (!parent) {
    throw new Error(`Parent entity "${def.extends}" not found for entity "${def.slug}"`)
  }

  // 子字段名集合
  const childFieldNames = new Set(
    def.fields.filter((f) => "name" in f).map((f) => (f as { name: string }).name)
  )

  // 父字段中子实体没有的追加到前面
  const inheritedFields = parent.fields.filter(
    (f) => "name" in f && !childFieldNames.has((f as { name: string }).name)
  )

  return {
    ...parent,
    ...def,
    fields: [...inheritedFields, ...def.fields],
    listView: { ...parent.listView, ...def.listView },
    formView: def.formView ?? parent.formView,
    kanbanView: def.kanbanView ?? parent.kanbanView,
    access: def.access ?? parent.access,
  }
}
