/**
 * 实体注册表：原子保存 bootstrap 已验证的 EntityDef 与受信任资源描述。
 *
 * @example
 * entityRegistry.replaceAll(definitions, resources)
 * const user = entityRegistry.requireResource("system.user")
 *
 * @author AaronZZH & Kiro
 */

import type { EntityResourceDescriptor } from "@/lib/api/rest/entity/entity-def"
import type { EntityDef } from "@/lib/types/entity"

import { builtinMixins, type MixinDef } from "./entity-mixins"
import { resolveExtends, resolveMixins } from "./entity-resolve"

class EntityRegistry {
  private raw = new Map<string, EntityDef>()
  private resolved = new Map<string, EntityDef>()
  private resources = new Map<string, EntityResourceDescriptor>()
  private customMixins: Record<string, MixinDef> = {}

  /** 注册实体（原始配置，未解析 mixin/extends）。 */
  register(def: EntityDef): void {
    this.raw.set(def.slug, def)
    this.resolved.delete(def.slug)
  }

  /** 批量注册实体。 */
  registerAll(defs: EntityDef[]): void {
    for (const def of defs) {
      this.register(def)
    }
  }

  /** 原子替换工作区 bootstrap 的 EntityDef 与受信任资源描述，避免消费者观察到部分状态。 */
  replaceAll(defs: EntityDef[], resources: EntityResourceDescriptor[]): void {
    const nextRaw = new Map<string, EntityDef>()
    const nextResources = new Map<string, EntityResourceDescriptor>()
    for (const def of defs) {
      nextRaw.set(def.slug, def)
    }
    for (const resource of resources) {
      if (nextResources.has(resource.resource)) {
        throw new Error(`重复的受信任资源：${resource.resource}`)
      }
      nextResources.set(resource.resource, resource)
    }

    this.raw = nextRaw
    this.resolved = new Map<string, EntityDef>()
    this.resources = nextResources
  }

  /** 按稳定资源标识获取受信任资源描述。 */
  getResource(resource: string): EntityResourceDescriptor | undefined {
    return this.resources.get(resource)
  }

  /** 严格获取关联目标的受信任资源描述。 */
  requireResource(resource: string): EntityResourceDescriptor {
    const descriptor = this.getResource(resource)
    if (!descriptor) {
      throw new Error(`未注册的关联资源：${resource}`)
    }
    return descriptor
  }

  /** 按稳定资源标识解析已加载 EntityDef；descriptor-only 资源返回 undefined。 */
  getByResource(resource: string): EntityDef | undefined {
    for (const definition of this.getAll()) {
      if (definition.resource === resource) return definition
    }
    return undefined
  }

  /** 注册自定义 Mixin。 */
  registerMixin(mixin: MixinDef): void {
    this.customMixins[mixin.name] = mixin
  }

  /** 获取解析后的实体定义（自动解析 mixins + extends）。 */
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

  /** 获取所有已解析的实体。 */
  getAll(): EntityDef[] {
    for (const slug of this.raw.keys()) {
      try {
        this.get(slug)
      } catch (_e) {}
    }
    return Array.from(this.resolved.values())
  }

  /** 按 group 分组获取实体。 */
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

  /** 清空注册表与自定义 Mixin（测试用）。 */
  clear(): void {
    this.raw.clear()
    this.resolved.clear()
    this.resources.clear()
    this.customMixins = {}
  }
}

/** 全局实体注册表单例。 */
export const entityRegistry = new EntityRegistry()
