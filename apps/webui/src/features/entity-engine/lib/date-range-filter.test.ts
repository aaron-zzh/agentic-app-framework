import { describe, expect, it } from "vitest"

import { buildDateRangeFilter, getDateRangeValues } from "./date-range-filter"

describe("日期范围筛选条件", () => {
  it("仅填写开始日期时构造 gte 条件", () => {
    expect(buildDateRangeFilter("dueDate", "2026-07-18", "")).toEqual({
      field: "dueDate",
      operator: "gte",
      values: ["2026-07-18"]
    })
  })

  it("仅填写结束日期时构造 lte 条件", () => {
    expect(buildDateRangeFilter("dueDate", "", "2026-07-21")).toEqual({
      field: "dueDate",
      operator: "lte",
      values: ["2026-07-21"]
    })
  })

  it("同时填写起止日期时构造 between 条件", () => {
    expect(buildDateRangeFilter("dueDate", "2026-07-18", "2026-07-21")).toEqual({
      field: "dueDate",
      operator: "between",
      values: ["2026-07-18", "2026-07-21"]
    })
  })

  it("将单端与双端条件还原为日期范围输入值", () => {
    expect(
      getDateRangeValues({ field: "dueDate", operator: "gte", values: ["2026-07-18"] })
    ).toEqual({
      start: "2026-07-18",
      end: ""
    })
    expect(
      getDateRangeValues({
        field: "dueDate",
        operator: "between",
        values: ["2026-07-18", "2026-07-21"]
      })
    ).toEqual({ start: "2026-07-18", end: "2026-07-21" })
  })
})
