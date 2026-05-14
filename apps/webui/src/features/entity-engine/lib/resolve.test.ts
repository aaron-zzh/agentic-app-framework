import { describe, expect, it } from "vitest"

import type { EntityDef, FieldDef } from "../types"

import { resolveExtends, resolveMixins } from "../lib"

/** 从 fields 中提取所有字段名 */
function fieldNames(fields: FieldDef[]): string[] {
  return fields.filter((f) => "name" in f).map((f) => (f as { name: string }).name)
}

const baseEntity: EntityDef = {
  slug: "task",
  label: "任务",
  apiPath: "/api/tasks",
  fields: [
    { type: "text", name: "title", required: true },
    { type: "select", name: "status", options: [{ label: "待办", value: "todo" }] },
  ],
  listView: { columns: ["title", "status"] },
  mixins: ["timestamp", "audit"],
}

describe("resolveMixins", () => {
  it("应合并 mixin 字段到末尾", () => {
    const resolved = resolveMixins(baseEntity)
    const names = fieldNames(resolved.fields)
    expect(names).toContain("createTime")
    expect(names).toContain("updateTime")
    expect(names).toContain("createBy")
    expect(names).toContain("updateBy")
    expect(names.indexOf("title")).toBeLessThan(names.indexOf("createTime"))
  })

  it("同名字段自身覆盖 mixin", () => {
    const entity: EntityDef = {
      ...baseEntity,
      fields: [
        ...baseEntity.fields,
        { type: "date", name: "createTime", label: "自定义创建时间" },
      ],
    }
    const resolved = resolveMixins(entity)
    const createTimeFields = resolved.fields.filter(
      (f) => "name" in f && (f as { name: string }).name === "createTime"
    )
    expect(createTimeFields).toHaveLength(1)
    expect(createTimeFields[0]).toHaveProperty("label", "自定义创建时间")
  })

  it("无 mixins 时原样返回", () => {
    const entity: EntityDef = { ...baseEntity, mixins: undefined }
    expect(resolveMixins(entity)).toBe(entity)
  })

  it("未知 mixin 抛错", () => {
    const entity: EntityDef = { ...baseEntity, mixins: ["nonexistent"] }
    expect(() => resolveMixins(entity)).toThrow('Mixin "nonexistent" not found')
  })
})

describe("resolveExtends", () => {
  const parentEntity: EntityDef = {
    slug: "base-doc",
    label: "基础文档",
    apiPath: "/api/base-docs",
    fields: [
      { type: "text", name: "title", required: true },
      { type: "date", name: "publishedAt" },
    ],
    listView: { columns: ["title", "publishedAt"], defaultSort: "publishedAt:desc" },
    formView: { autosave: { enabled: true, debounceMs: 2000 } },
  }

  const childEntity: EntityDef = {
    slug: "article",
    label: "文章",
    apiPath: "/api/articles",
    extends: "base-doc",
    fields: [
      { type: "richText", name: "content" },
      { type: "text", name: "title", label: "文章标题" },
    ],
    listView: { columns: ["title", "content"] },
  }

  const getParent = (slug: string) => (slug === "base-doc" ? parentEntity : undefined)

  it("应继承父实体字段", () => {
    const resolved = resolveExtends(childEntity, getParent)
    const names = fieldNames(resolved.fields)
    expect(names).toContain("publishedAt")
    expect(names).toContain("content")
  })

  it("子字段覆盖父同名字段", () => {
    const resolved = resolveExtends(childEntity, getParent)
    const titleField = resolved.fields.find(
      (f) => "name" in f && (f as { name: string }).name === "title"
    )
    expect(titleField).toHaveProperty("label", "文章标题")
  })

  it("listView 浅合并", () => {
    const resolved = resolveExtends(childEntity, getParent)
    expect(resolved.listView.columns).toEqual(["title", "content"])
    expect(resolved.listView.defaultSort).toBe("publishedAt:desc")
  })

  it("formView 子无则继承父", () => {
    const resolved = resolveExtends(childEntity, getParent)
    expect(resolved.formView?.autosave?.enabled).toBe(true)
  })

  it("无 extends 时原样返回", () => {
    const entity: EntityDef = { ...childEntity, extends: undefined }
    expect(resolveExtends(entity, getParent)).toBe(entity)
  })

  it("父实体不存在抛错", () => {
    expect(() => resolveExtends(childEntity, () => undefined)).toThrow(
      'Parent entity "base-doc" not found'
    )
  })
})
