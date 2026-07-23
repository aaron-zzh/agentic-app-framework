/**
 * use-filter-params.ts 单元测试——验证筛选条件 URL 编码
 */

import { describe, expect, it } from "vitest"

import { decodeFilterParams, encodeFilterParams } from "./use-filter-params"

describe("encodeFilterParams", () => {
  it("应将复合筛选条件编码到唯一 filter 参数并可完整解码", () => {
    const conditions = [
      { field: "dueDate", operator: "between", values: ["$now", "$nowPlus3Days"] },
      { field: "status", operator: "eq", values: ["pending"] },
      { field: "category", operator: "in", values: ["待办", "电话"] },
      { field: "dueDate", operator: "isNull", values: [] }
    ]

    const encoded = encodeFilterParams(conditions)

    expect(Object.keys(encoded)).toEqual(["filter"])
    expect(decodeFilterParams(new URLSearchParams(encoded))).toEqual(conditions)
  })
})
