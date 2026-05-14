import { beforeEach, describe, expect, it } from "vitest"

import { sampleEntities } from "../entities"

import { entityRegistry } from "./registry"

describe("entityRegistry", () => {
  beforeEach(() => {
    entityRegistry.clear()
    entityRegistry.registerAll(sampleEntities)
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
    expect(all).toHaveLength(3)
  })

  it("getByGroup 按 group 分组", () => {
    const groups = entityRegistry.getByGroup()
    expect(groups.content).toHaveLength(1)
    expect(groups.system).toHaveLength(1)
    expect(groups.project).toHaveLength(1)
  })

  it("解析结果应缓存", () => {
    const first = entityRegistry.get("document")
    const second = entityRegistry.get("document")
    expect(first).toBe(second)
  })
})
