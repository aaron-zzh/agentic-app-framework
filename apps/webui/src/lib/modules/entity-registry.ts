/**
 * 实体注册表：管理所有 EntityDef 的注册、解析和查找
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```ts
 * import { entityRegistry } from "@/lib/modules/entity-registry"
 *
 * // 注册实体
 * entityRegistry.registerAll([documentEntity, taskEntity])
 *
 * // 查找（自动解析 mixins + extends）
 * const doc = entityRegistry.get("document")
 *
 * // 按分组获取（侧边栏菜单）
 * const groups = entityRegistry.getByGroup()
 * ```
 */

import type { EntityDef } from "@/lib/types/entity"

import { builtinMixins, type MixinDef } from "./entity-mixins"
import { resolveExtends, resolveMixins } from "./entity-resolve"

class EntityRegistry {
  private raw = new Map<string, EntityDef>()
  private resolved = new Map<string, EntityDef>()
  private customMixins: Record<string, MixinDef> = {}

  /** 注册实体（原始配置，未解析 mixin/extends） */
  register(def: EntityDef): void {
    this.raw.set(def.slug, def)
    this.resolved.delete(def.slug)
  }

  /** 批量注册 */
  registerAll(defs: EntityDef[]): void {
    for (const def of defs) {
      this.register(def)
    }
  }

  /** 注册自定义 Mixin */
  registerMixin(mixin: MixinDef): void {
    this.customMixins[mixin.name] = mixin
  }

  /** 获取解析后的实体定义（自动解析 mixins + extends） */
  get(slug: string): EntityDef | undefined {
    if (this.resolved.has(slug)) return this.resolved.get(slug)

    const raw = this.raw.get(slug)
    if (!raw) return undefined

    const allMixins = { ...builtinMixins, ...this.customMixins }
    let def = resolveMixins(raw, allMixins)
    def = resolveExtends(def, (parentSlug) => {
      const parentRaw = this.raw.get(parentSlug)
      if (!parentRaw) return undefined
      return resolveMixins(parentRaw, allMixins)
    })

    this.resolved.set(slug, def)
    return def
  }

  /** 获取所有已解析的实体 */
  getAll(): EntityDef[] {
    for (const slug of this.raw.keys()) {
      try {
        this.get(slug)
      } catch (_e) {}
    }
    return Array.from(this.resolved.values())
  }

  /** 按 group 分组获取实体 */
  getByGroup(): Record<string, EntityDef[]> {
    const all = this.getAll()
    const groups: Record<string, EntityDef[]> = {}
    for (const def of all) {
      const group = def.group ?? "other"
      if (!groups[group]) groups[group] = []
      groups[group].push(def)
    }
    return groups
  }

  /** 清空注册表（测试用） */
  clear(): void {
    this.raw.clear()
    this.resolved.clear()
    this.customMixins = {}
  }
}

/** 全局实体注册表单例 */
export const entityRegistry = new EntityRegistry()
