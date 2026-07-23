/**
 * buildZodSchema 单元测试
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"

import type { FieldDef } from "../../types"
import { buildZodSchema } from "./build-zod-schema"

describe("buildZodSchema", () => {
  it("should generate schema for required text field", () => {
    const fields: FieldDef[] = [{ type: "text", name: "title", required: true }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ title: "hello" }).success).toBe(true)
    expect(schema.safeParse({}).success).toBe(false)
  })

  it("should report a field-level required message for missing required values", () => {
    const fields: FieldDef[] = [
      { type: "text", name: "title", label: "标题", required: true },
      { type: "select", name: "tags", label: "标签", multiple: true, required: true },
      { type: "number", name: "priority", label: "优先级", required: true }
    ]
    const schema = buildZodSchema(fields)

    const result = schema.safeParse({ title: " ", tags: [], priority: Number.NaN })

    expect(result.success).toBe(false)
    if (result.success) return
    expect(result.error.issues.map((issue) => issue.message)).toEqual([
      "标题不能为空",
      "标签不能为空",
      "优先级不能为空"
    ])
  })

  it("should allow empty optional field", () => {
    const fields: FieldDef[] = [{ type: "text", name: "note" }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({}).success).toBe(true)
  })

  it("should validate email format", () => {
    const fields: FieldDef[] = [{ type: "email", name: "email", required: true }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ email: "test@example.com" }).success).toBe(true)
    expect(schema.safeParse({ email: "invalid" }).success).toBe(false)
  })

  it("should validate number min/max", () => {
    const fields: FieldDef[] = [{ type: "number", name: "age", required: true, min: 0, max: 150 }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ age: 25 }).success).toBe(true)
    expect(schema.safeParse({ age: -1 }).success).toBe(false)
    expect(schema.safeParse({ age: 200 }).success).toBe(false)
  })

  it("should validate select enum", () => {
    const fields: FieldDef[] = [
      {
        type: "select",
        name: "status",
        required: true,
        options: [
          { label: "Draft", value: "draft" },
          { label: "Published", value: "published" }
        ]
      }
    ]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ status: "draft" }).success).toBe(true)
    expect(schema.safeParse({ status: "invalid" }).success).toBe(false)
  })

  it("should handle checkbox as boolean", () => {
    const fields: FieldDef[] = [{ type: "checkbox", name: "agree", required: true }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ agree: true }).success).toBe(true)
  })

  it("should skip layout fields", () => {
    const fields: FieldDef[] = [
      { type: "text", name: "title", required: true },
      { type: "group", label: "Group", fields: [] },
      { type: "tabs", tabs: [] }
    ]
    const schema = buildZodSchema(fields)
    expect(Object.keys(schema.shape)).toEqual(["title"])
  })

  it("should enforce text maxLength", () => {
    const fields: FieldDef[] = [{ type: "text", name: "code", required: true, maxLength: 5 }]
    const schema = buildZodSchema(fields)
    expect(schema.safeParse({ code: "abc" }).success).toBe(true)
    expect(schema.safeParse({ code: "abcdef" }).success).toBe(false)
  })
})
