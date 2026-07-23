/**
 * sort-params.ts 单元测试——验证 URL 与 TanStack 排序状态协议
 */

import { describe, expect, it } from "vitest"
import { parseSortParam, resolveSortingUpdater, serializeSorting } from "./sort-params"

describe("sort-params", () => {
  it("应保留多字段排序的方向与优先级", () => {
    const sorting = parseSortParam("status:asc,createTime:desc")

    expect(sorting).toEqual([
      { id: "status", desc: false },
      { id: "createTime", desc: true }
    ])
    expect(serializeSorting(sorting)).toBe("status:asc,createTime:desc")
  })

  it("应拒绝旧排序格式和重复字段", () => {
    expect(() => parseSortParam("-createTime")).toThrow("排序格式非法")
    expect(() => parseSortParam("id:asc,id:desc")).toThrow("排序格式非法")
  })

  it("应支持 TanStack 的函数式排序更新", () => {
    const next = resolveSortingUpdater(
      (previous) => [...previous, { id: "title", desc: false }],
      [{ id: "status", desc: true }]
    )

    expect(next).toEqual([
      { id: "status", desc: true },
      { id: "title", desc: false }
    ])
  })
})
