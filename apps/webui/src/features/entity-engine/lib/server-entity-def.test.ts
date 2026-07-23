/**
 * 服务端 EntityDef bootstrap 解析器测试
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"

import type { EntityDefRecord, EntityResourceDescriptor } from "@/lib/api/rest/entity/entity-def"

import { parseEntityDefBootstrap } from "./server-entity-def"

const documentResource: EntityResourceDescriptor = {
  resource: "content.document",
  slug: "document",
  apiPath: "/documents",
  fields: ["id", "title", "author"],
  referenceable: true
}

const userResource: EntityResourceDescriptor = {
  resource: "system.user",
  slug: "user",
  apiPath: "/system/users",
  fields: ["id", "label"],
  referenceable: false
}

const documentRecord: EntityDefRecord = {
  id: 1,
  slug: "document",
  apiPath: "/documents",
  config: {
    kind: "code",
    resource: "content.document",
    label: "文档",
    apiPath: "/untrusted-config-path",
    fields: [
      { type: "text", name: "title", label: "标题" },
      { type: "relationship", name: "author", relationTo: "system.user" }
    ],
    listView: { columns: ["title", "author"] }
  },
  builtin: true,
  enabled: true,
  createTime: "2026-07-18T00:00:00Z",
  updateTime: "2026-07-18T00:00:00Z"
}

describe("parseEntityDefBootstrap", () => {
  it("使用顶层 apiPath，并允许关系目标仅有资源描述", () => {
    const result = parseEntityDefBootstrap({
      definitions: [documentRecord],
      resources: [documentResource, userResource]
    })

    expect(result.errors).toEqual([])
    expect(result.definitions).toHaveLength(1)
    expect(result.definitions[0]?.apiPath).toBe("/documents")
    expect(result.resources).toEqual([documentResource, userResource])
  })

  it("拒绝与 EntityDef 运行期路由不一致的资源描述", () => {
    const result = parseEntityDefBootstrap({
      definitions: [documentRecord],
      resources: [{ ...documentResource, apiPath: "/other-documents" }, userResource]
    })

    expect(result.errors).toContain("document: resource apiPath 与受信任描述不一致")
  })

  it("拒绝未随 bootstrap 返回的关系资源", () => {
    const result = parseEntityDefBootstrap({
      definitions: [documentRecord],
      resources: [documentResource]
    })

    expect(result.errors).toContain("document: 关联资源未出现在 bootstrap 中：system.user")
  })

  it("拒绝缺失、空白或重复的资源字段白名单", () => {
    const invalidResources = [
      { resource: "content.document", slug: "document", apiPath: "/documents" },
      { ...documentResource, fields: ["id", " "] },
      { ...documentResource, fields: ["id", "id"] }
    ]

    const result = parseEntityDefBootstrap({ definitions: [], resources: invalidResources })

    expect(result.errors).toContain("content.document: 资源描述 fields 非法")
    expect(result.errors).toContain("content.document: 资源描述 fields 重复")
  })
})
