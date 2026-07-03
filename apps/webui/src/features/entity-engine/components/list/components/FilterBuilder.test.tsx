/**
 * FilterBuilder.tsx 单元测试——验证操作符推断与筛选值防抖
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { EntityDef } from "@/lib/types/entity"
import { FilterBuilder } from "./FilterBuilder"

const mockEntity: Partial<EntityDef> = {
  fields: [
    { name: "name", label: "名称", type: "text" },
    { name: "amount", label: "金额", type: "number" },
    { name: "enabled", label: "启用", type: "checkbox" }
  ]
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe("FilterBuilder", () => {
  it("已有 filters 时点击 + 添加筛选应展开条件面板", () => {
    const filters = [{ field: "name", operator: "contains", value: "" }]
    render(<FilterBuilder entity={mockEntity as EntityDef} filters={filters} onChange={vi.fn()} />)

    fireEvent.click(screen.getByText("+ 添加筛选"))

    expect(screen.getAllByPlaceholderText("值").length).toBeGreaterThan(0)
  })

  it("checkbox 字段的操作符应为 isTrue/isFalse 且不显示值输入框", () => {
    const filters = [{ field: "enabled", operator: "isTrue", value: "" }]
    render(<FilterBuilder entity={mockEntity as EntityDef} filters={filters} onChange={vi.fn()} />)

    fireEvent.click(screen.getByText("+ 添加筛选"))

    expect(screen.queryByPlaceholderText("值")).not.toBeInTheDocument()
    expect(screen.getByText("是")).toBeInTheDocument()
    expect(screen.getByText("否")).toBeInTheDocument()
  })

  it("输入筛选值时应防抖 300ms 后才触发 onChange", () => {
    const onChange = vi.fn()
    const filters = [{ field: "name", operator: "contains", value: "" }]
    render(<FilterBuilder entity={mockEntity as EntityDef} filters={filters} onChange={onChange} />)

    // 展开面板（会调用一次 onChange 追加新条件，与本次断言的输入防抖无关，先清空调用记录）
    fireEvent.click(screen.getByText("+ 添加筛选"))
    onChange.mockClear()

    const input = screen.getByPlaceholderText("值")

    fireEvent.change(input, { target: { value: "M" } })
    fireEvent.change(input, { target: { value: "MU" } })
    fireEvent.change(input, { target: { value: "MUSIC" } })

    // 防抖窗口内不应触发 onChange
    expect(onChange).not.toHaveBeenCalled()

    vi.advanceTimersByTime(300)

    // 300ms 后只应触发一次，且是最终值（onChange 为 mock，不会真正更新 filters prop）
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange).toHaveBeenCalledWith([{ field: "name", operator: "contains", value: "MUSIC" }])
  })

  it("number 字段应提供 gte/lte 操作符", () => {
    const filters = [{ field: "amount", operator: "eq", value: "" }]
    render(<FilterBuilder entity={mockEntity as EntityDef} filters={filters} onChange={vi.fn()} />)

    fireEvent.click(screen.getByText("+ 添加筛选"))

    expect(screen.getByText("大于等于")).toBeInTheDocument()
    expect(screen.getByText("小于等于")).toBeInTheDocument()
  })
})
