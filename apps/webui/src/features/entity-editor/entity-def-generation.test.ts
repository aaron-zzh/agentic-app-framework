import { describe, expect, it } from "vitest"

import type { EntityResourceDescriptor } from "@/lib/api/rest/entity/entity-def"

import {
  buildEntityDefGeneratorSystemPrompt,
  extractGeneratedEntityDefJson,
  validateGeneratedEntityDef
} from "./entity-def-generation"

const resources: EntityResourceDescriptor[] = [
  {
    resource: "system.todo",
    slug: "todo",
    apiPath: "/todos",
    fields: ["id", "title", "status", "assignee", "assigneeId"],
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

describe("entity-def generation", () => {
  it("将可信资源字段白名单和当前草稿注入提示词", () => {
    const prompt = buildEntityDefGeneratorSystemPrompt(
      resources,
      '{"kind":"code","resource":"system.todo","label":"待办","fields":[],"listView":{"columns":["title"]}}'
    )

    expect(prompt).toContain('"resource":"system.todo"')
    expect(prompt).toContain('"assigneeId"')
    expect(prompt).toContain("filterFields")
    expect(prompt).toContain("当前已校验草稿")
    expect(prompt).toContain("禁止输出 slug、apiPath")
  })

  it("接受只引用可信资源和字段的完整草稿", () => {
    const result = validateGeneratedEntityDef(
      {
        kind: "code",
        resource: "system.todo",
        label: "待办",
        fields: [
          { type: "text", name: "title" },
          {
            type: "relationship",
            name: "assignee",
            relationTo: "system.user",
            writeKey: "assigneeId"
          }
        ],
        listView: {
          columns: ["title", "assignee"],
          filterFields: ["title"],
          defaultSort: "title:asc"
        }
      },
      resources
    )

    expect(result.errors).toEqual([])
    expect(result.config?.resource).toBe("system.todo")
  })

  it("拒绝伪造路由、未授权字段和未注册关系资源", () => {
    const result = validateGeneratedEntityDef(
      {
        kind: "code",
        resource: "system.todo",
        slug: "todo",
        fields: [
          { type: "relationship", name: "owner", relationTo: "system.unknown", pickerPath: "/fake" }
        ],
        listView: { columns: ["owner"], filterFields: ["owner"] }
      },
      resources
    )

    expect(result.config).toBeNull()
    expect(result.errors).toContain("配置不得包含 slug 或 apiPath")
    expect(result.errors).toContain("fields[0].name 引用了未授权字段：owner")
    expect(result.errors).toContain("fields[0].relationTo 引用了未授权资源：system.unknown")
    expect(result.errors).toContain("fields[0] 不允许声明 pickerPath")
    expect(result.errors).toContain("listView.filterFields[0] 引用了未授权字段：owner")
  })

  it("拒绝不受支持的字段类型", () => {
    const result = validateGeneratedEntityDef(
      {
        kind: "code",
        resource: "system.todo",
        label: "待办",
        fields: [{ type: "cascader", name: "title" }],
        listView: { columns: ["title"] }
      },
      resources
    )

    expect(result.config).toBeNull()
    expect(result.errors).toContain("fields[0].type 不受生成器支持：cascader")
  })

  it("仅从 json 代码块提取可解析配置", () => {
    expect(extractGeneratedEntityDefJson('说明\n```json\n{"kind":"code"}\n```')).toBe(
      '{"kind":"code"}'
    )
    expect(extractGeneratedEntityDefJson("not json")).toBeNull()
  })
})
