/**
 * 实体注册表：原子保存 bootstrap 已验证的 EntityDef 与受信任资源描述。
 * EntityDef 在 replaceAll 时一次性解析 mixins + extends，解析失败直接抛出。
 *
 * @example
 * entityRegistry.replaceAll(definitions, resources)
 * const user = entityRegistry.requireResource("system.user")
 *
 * @author AaronZZH & Kiro
 */

import type { EntityResourceDescriptor } from "@/lib/api/rest/entity"
import type { EntityDef } from "@/lib/types/entity"

import { builtinMixins, type MixinDef } from "./entity-mixins"
import { resolveExtends, resolveMixins } from "./entity-resolve"

class EntityRegistry {
  private definitions = new Map<string, EntityDef>()
  private resources = new Map<string, EntityResourceDescriptor>()
  private customMixins: Record<string, MixinDef> = {}

  /** 注册自定义 Mixin；需在 replaceAll 之前注册，才会参与实体解析。 */
  registerMixin(mixin: MixinDef): void {
    this.customMixins[mixin.name] = mixin
  }

  /** 原子替换工作区 bootstrap 的 EntityDef 与受信任资源描述，避免消费者观察到部分状态。 */
  replaceAll(defs: EntityDef[], resources: EntityResourceDescriptor[]): void {
    const nextResources = new Map<string, EntityResourceDescriptor>()
    for (const resource of resources) {
      if (nextResources.has(resource.resource)) {
        throw new Error(`重复的受信任资源：${resource.resource}`)
      }
      nextResources.set(resource.resource, resource)
    }

    const allMixins = { ...builtinMixins, ...this.customMixins }
    const rawBySlug = new Map(defs.map((def) => [def.slug, def]))
    const nextDefinitions = new Map<string, EntityDef>()
    for (const def of defs) {
      nextDefinitions.set(
        def.slug,
        resolveExtends(resolveMixins(def, allMixins), (parentSlug) => {
          const parent = rawBySlug.get(parentSlug)
          return parent && resolveMixins(parent, allMixins)
        })
      )
    }

    this.definitions = nextDefinitions
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
    return this.getAll().find((definition) => definition.resource === resource)
  }

  /** 按 slug 获取已解析的实体定义。 */
  get(slug: string): EntityDef | undefined {
    return this.definitions.get(slug)
  }

  /** 获取所有已解析的实体。 */
  getAll(): EntityDef[] {
    return Array.from(this.definitions.values())
  }

  /** 清空注册表与自定义 Mixin（测试用）。 */
  clear(): void {
    this.definitions = new Map()
    this.resources = new Map()
    this.customMixins = {}
  }
}

/** 全局实体注册表单例。 */
export const entityRegistry = new EntityRegistry()
