import { beforeEach, describe, expect, it } from "vitest"

import type { EntityResourceDescriptor } from "@/lib/api/rest/entity"
import type { EntityDef } from "../types"
import { entityRegistry } from "./registry"

const testEntities: EntityDef[] = [
  {
    kind: "code",
    resource: "content.document",
    slug: "document",
    label: "文档",
    apiPath: "/documents",
    group: "content",
    fields: [{ type: "text", name: "title", label: "标题" }],
    listView: { columns: ["title"] },
    mixins: ["baseEntity"]
  },
  {
    kind: "code",
    resource: "project.task",
    slug: "task",
    label: "任务",
    apiPath: "/tasks",
    group: "project",
    fields: [{ type: "text", name: "title", label: "标题" }],
    listView: { columns: ["title"] }
  }
]

const testResources: EntityResourceDescriptor[] = [
  {
    resource: "content.document",
    slug: "document",
    apiPath: "/documents",
    fields: ["id", "title"],
    referenceable: true
  },
  {
    resource: "project.task",
    slug: "task",
    apiPath: "/tasks",
    fields: ["id", "title"],
    referenceable: true
  },
  {
    resource: "system.user",
    slug: "user",
    apiPath: "/system/users",
    fields: ["id", "label"],
    referenceable: false
  }
]

describe("entityRegistry", () => {
  beforeEach(() => {
    entityRegistry.clear()
    entityRegistry.replaceAll(testEntities, testResources)
  })

  it("应通过 slug 获取实体", () => {
    const doc = entityRegistry.get("document")
    expect(doc).toBeDefined()
    expect(doc?.label).toBe("文档")
  })

  it("不存在的 slug 返回 undefined", () => {
    expect(entityRegistry.get("nonexistent")).toBeUndefined()
  })

  it("应自动解析 mixin 字段", () => {
    const doc = entityRegistry.get("document")
    const names = doc?.fields.filter((f) => "name" in f).map((f) => (f as { name: string }).name)
    expect(names).toContain("createTime")
    expect(names).toContain("updateBy")
    expect(names).toContain("remark")
  })

  it("getAll 返回所有实体", () => {
    const all = entityRegistry.getAll()
    expect(all).toHaveLength(2)
  })

  it("getByGroup 按 group 分组", () => {
    const groups = entityRegistry.getByGroup()
    expect(groups.content).toHaveLength(1)
    expect(groups.project).toHaveLength(1)
  })

  it("应解析 descriptor-only 关联资源，但不将其视为已加载实体", () => {
    expect(entityRegistry.requireResource("system.user")).toEqual({
      resource: "system.user",
      slug: "user",
      apiPath: "/system/users",
      fields: ["id", "label"],
      referenceable: false
    })
    expect(entityRegistry.getByResource("system.user")).toBeUndefined()
  })

  it("解析结果应缓存", () => {
    const first = entityRegistry.get("document")
    const second = entityRegistry.get("document")
    expect(first).toBe(second)
  })
})
