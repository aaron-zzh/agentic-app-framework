/**
 * build-columns.tsx 单元测试——验证服务端排序 capability 与 UI 列配置交集
 */

import { describe, expect, it } from "vitest"
import type { EntityDef } from "../types"
import { buildColumns } from "./build-columns"

const entity = {
  fields: [
    { name: "title", label: "标题", type: "text" },
    { name: "status", label: "状态", type: "select" }
  ]
} as unknown as EntityDef

describe("buildColumns", () => {
  it("仅应使能服务端 capability 声明的字段", () => {
    const columns = buildColumns(entity, [{ name: "title" }, { name: "status" }], ["title"])

    expect(columns[0].enableSorting).toBe(true)
    expect(columns[1].enableSorting).toBe(false)
  })

  it("应允许 EntityDef 在已授权字段上关闭排序入口", () => {
    const columns = buildColumns(entity, [{ name: "title", sortable: false }], ["title"])

    expect(columns[0].enableSorting).toBe(false)
  })
})
