/**
 * 服务端 EntityDef 解析器——仅接受 JSON 可序列化的代码实体元数据。
 * @author AaronZZH & Kiro
 */

import type { EntityDefRecord, EntityResourceDescriptor } from "@/lib/api/rest/entity"
import type { EntityDef, FieldDef } from "../types"

interface ParseResult {
  definitions: EntityDef[]
  errors: string[]
}

export interface EntityBootstrapParseResult extends ParseResult {
  resources: EntityResourceDescriptor[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0
}

function isFieldDefArray(value: unknown): value is FieldDef[] {
  return (
    Array.isArray(value) &&
    value.every(
      (field) =>
        isRecord(field) &&
        typeof field.type === "string" &&
        (field.type === "group" ||
          field.type === "tabs" ||
          field.type === "row" ||
          typeof field.name === "string")
    )
  )
}

function referencedResources(fields: FieldDef[]): string[] {
  const resources: string[] = []
  const collect = (field: FieldDef): void => {
    switch (field.type) {
      case "group":
        field.fields.forEach(collect)
        break
      case "tabs":
        field.tabs.forEach((tab) => {
          tab.fields.forEach(collect)
        })
        break
      case "row":
        field.fields.forEach(collect)
        break
      case "relationship":
        resources.push(field.relationTo)
        break
      case "cascader":
        resources.push(...field.levels.map((level) => level.relationTo))
        break
    }
  }
  fields.forEach(collect)
  return resources
}

function parseResourceDescriptor(value: unknown): EntityResourceDescriptor | string {
  if (!isRecord(value)) return "资源描述必须是对象"
  if (!isNonBlankString(value.resource)) return "资源描述缺少 resource"
  if (!isNonBlankString(value.slug)) return `${value.resource}: 资源描述缺少 slug`
  if (!isNonBlankString(value.apiPath) || !value.apiPath.startsWith("/")) {
    return `${value.resource}: 资源描述 apiPath 非法`
  }
  if (!Array.isArray(value.fields) || !value.fields.every(isNonBlankString)) {
    return `${value.resource}: 资源描述 fields 非法`
  }
  if (typeof value.referenceable !== "boolean") {
    return `${value.resource}: 资源描述 referenceable 非法`
  }
  const fields = value.fields.map((field) => field.trim())
  if (new Set(fields).size !== fields.length) {
    return `${value.resource}: 资源描述 fields 重复`
  }
  return {
    resource: value.resource,
    slug: value.slug,
    apiPath: value.apiPath,
    fields,
    referenceable: value.referenceable
  }
}

/** 解析单条服务端 EntityDef；失败信息可呈现给管理员而不静默降级。 */
export function parseServerEntityDef(record: EntityDefRecord): EntityDef | string {
  const config = record.config
  if (!isRecord(config)) return `${record.slug}: config 必须是对象`
  if (!isNonBlankString(record.slug)) return "实体元数据缺少服务端 slug"
  if (!isNonBlankString(record.apiPath) || !record.apiPath.startsWith("/")) {
    return `${record.slug}: 服务端 apiPath 非法`
  }
  if (config.kind !== "code") return `${record.slug}: 仅支持 kind=code`
  if (!isNonBlankString(config.resource)) return `${record.slug}: 缺少 resource`
  if (!isNonBlankString(config.label)) return `${record.slug}: 缺少 label`
  if (config.accessMode !== undefined && config.accessMode !== "admin-maintenance") {
    return `${record.slug}: accessMode 非法`
  }
  if (!isFieldDefArray(config.fields)) return `${record.slug}: fields 非法`
  if (!isRecord(config.listView)) return `${record.slug}: listView 非法`

  return {
    ...config,
    slug: record.slug,
    apiPath: record.apiPath
  } as unknown as EntityDef
}

/** 批量解析 EntityDef-only 响应并保留全部错误。 */
export function parseServerEntityDefs(records: EntityDefRecord[]): ParseResult {
  const definitions: EntityDef[] = []
  const errors: string[] = []
  const resourceSlugs = new Map<string, string>()
  for (const record of records) {
    const parsed = parseServerEntityDef(record)
    if (typeof parsed === "string") {
      errors.push(parsed)
      continue
    }

    const resource = parsed.resource
    if (!resource) {
      errors.push(`${record.slug}: 缺少 resource`)
      continue
    }
    const previousSlug = resourceSlugs.get(resource)
    if (previousSlug) {
      errors.push(`${record.slug}: resource 与 ${previousSlug} 重复：${resource}`)
      continue
    }

    resourceSlugs.set(resource, parsed.slug)
    definitions.push(parsed)
  }
  return { definitions, errors }
}

/**
 * 解析工作区 bootstrap 响应；所有 EntityDef 必须与同载荷中的受信任资源描述一致。
 * descriptor-only 资源合法，可作为关系选择目标但不会生成实体详情路由。
 */
export function parseEntityDefBootstrap(payload: unknown): EntityBootstrapParseResult {
  if (!isRecord(payload)) {
    return { definitions: [], resources: [], errors: ["实体 bootstrap 响应必须是对象"] }
  }
  if (!Array.isArray(payload.definitions)) {
    return { definitions: [], resources: [], errors: ["实体 bootstrap 缺少 definitions 数组"] }
  }
  if (!Array.isArray(payload.resources)) {
    return { definitions: [], resources: [], errors: ["实体 bootstrap 缺少 resources 数组"] }
  }

  const resources: EntityResourceDescriptor[] = []
  const errors: string[] = []
  const resourcesById = new Map<string, EntityResourceDescriptor>()
  for (const value of payload.resources) {
    const parsed = parseResourceDescriptor(value)
    if (typeof parsed === "string") {
      errors.push(parsed)
      continue
    }
    if (resourcesById.has(parsed.resource)) {
      errors.push(`资源描述重复：${parsed.resource}`)
      continue
    }
    resourcesById.set(parsed.resource, parsed)
    resources.push(parsed)
  }

  const records: EntityDefRecord[] = []
  for (const value of payload.definitions) {
    if (!isRecord(value)) {
      errors.push("实体定义记录必须是对象")
      continue
    }
    records.push(value as unknown as EntityDefRecord)
  }
  const parsedDefinitions = parseServerEntityDefs(records)
  errors.push(...parsedDefinitions.errors)

  for (const definition of parsedDefinitions.definitions) {
    const resource = definition.resource
    const descriptor = resource ? resourcesById.get(resource) : undefined
    if (!descriptor) {
      errors.push(`${definition.slug}: 缺少受信任资源描述 ${resource ?? ""}`)
      continue
    }
    if (descriptor.slug !== definition.slug) {
      errors.push(`${definition.slug}: resource slug 与受信任描述不一致`)
    }
    if (descriptor.apiPath !== definition.apiPath) {
      errors.push(`${definition.slug}: resource apiPath 与受信任描述不一致`)
    }
    definition.referenceable = descriptor.referenceable
    for (const relationResource of new Set(referencedResources(definition.fields))) {
      if (!resourcesById.has(relationResource)) {
        errors.push(`${definition.slug}: 关联资源未出现在 bootstrap 中：${relationResource}`)
      }
    }
  }

  return { definitions: parsedDefinitions.definitions, resources, errors }
}
