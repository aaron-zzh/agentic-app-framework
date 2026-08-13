/**
 * SearchBar.tsx 单元测试——验证搜索条件构建、预设筛选与已应用条件展示
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { CrudFilterFieldMeta } from "@/lib/api/rest/crud"
import type { EntityDef } from "@/lib/types/entity"

import { SearchBar } from "./SearchBar"

const quickFilter = {
  label: "待处理",
  conditions: [{ field: "status", operator: "eq", values: ["pending"] }]
}

const capabilities: CrudFilterFieldMeta[] = [
  {
    field: "name",
    operators: [{ value: "contains", minValues: 1, maxValues: 1 }],
    variables: []
  },
  {
    field: "status",
    operators: [{ value: "eq", minValues: 1, maxValues: 1 }],
    variables: []
  },
  {
    field: "amount",
    operators: [{ value: "gt", minValues: 1, maxValues: 1 }],
    variables: []
  }
]

const mockEntity: Partial<EntityDef> = {
  fields: [
    { name: "name", label: "名称", type: "string" },
    { name: "status", label: "状态", type: "select", options: ["active", "inactive"] },
    { name: "amount", label: "金额", type: "number" },
    { name: "createdAt", label: "创建时间", type: "datetime" }
  ],
  listView: {
    filterableFields: ["name", "status", "amount"],
    quickFilters: [quickFilter],
    columns: []
  }
}

function renderSearchBar(
  overrides: Partial<React.ComponentProps<typeof SearchBar>> = {}
): ReturnType<typeof render> {
  return render(
    <SearchBar
      entity={mockEntity as EntityDef}
      filters={[]}
      onChange={vi.fn()}
      capabilities={capabilities}
      {...overrides}
    />
  )
}

describe("SearchBar", () => {
  it("应渲染搜索输入框", () => {
    renderSearchBar()

    expect(screen.getByRole("textbox")).toBeInTheDocument()
  })

  it("输入文字时应显示字段建议", () => {
    renderSearchBar()

    const input = screen.getByRole("textbox")
    fireEvent.change(input, { target: { value: "名" } })

    expect(screen.getByText("名称")).toBeInTheDocument()
  })

  it("仅显示后端授予 FILTER capability 的字段", () => {
    renderSearchBar({ capabilities: [capabilities[0]] })

    fireEvent.focus(screen.getByRole("textbox"))

    expect(screen.getByText("名称")).toBeInTheDocument()
    expect(screen.queryByText("状态")).not.toBeInTheDocument()
    expect(screen.queryByText("金额")).not.toBeInTheDocument()
  })

  it("为字段使用后端支持的操作符", () => {
    const onChange = vi.fn()
    renderSearchBar({ onChange })

    const input = screen.getByRole("textbox")
    fireEvent.change(input, { target: { value: "金额" } })
    fireEvent.click(screen.getByRole("button", { name: "金额" }))
    fireEvent.change(input, { target: { value: "100" } })
    fireEvent.keyDown(input, { key: "Enter" })

    expect(onChange).toHaveBeenCalledWith([
      { field: "amount", operator: "gt", values: ["100"] }
    ])
  })

  it("在搜索下拉中选择预设筛选时应用完整条件", () => {
    const onChange = vi.fn()
    renderSearchBar({ onChange })

    fireEvent.focus(screen.getByRole("textbox"))
    fireEvent.click(screen.getByRole("button", { name: "待处理" }))

    expect(onChange).toHaveBeenCalledWith(quickFilter.conditions)
  })

  it("已选预设在搜索下拉中显示勾选标记", () => {
    renderSearchBar({ filters: quickFilter.conditions })

    fireEvent.focus(screen.getByRole("textbox"))

    expect(screen.getByRole("img", { name: "待处理已选中" })).toHaveTextContent("✓")
  })

  it("完整命中的预设筛选显示为可关闭 Chip 并隐藏底层条件", () => {
    const onChange = vi.fn()
    renderSearchBar({ filters: quickFilter.conditions, onChange })

    expect(screen.getByText("待处理")).toBeInTheDocument()
    expect(screen.queryByText("状态 等于 pending")).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "移除待处理筛选" }))

    expect(onChange).toHaveBeenCalledWith([])
  })

  it("已有普通搜索条件时应显示带操作符的 tag", () => {
    const filters = [{ field: "name", operator: "contains", values: ["张"] }]
    renderSearchBar({ filters })

    expect(screen.getByText("名称 包含 张")).toBeInTheDocument()
  })

  it("高级筛选以带操作符的 Chip 展示且可单独移除", () => {
    const onChange = vi.fn()
    const filters = [{ field: "amount", operator: "gt", values: ["100"] }]
    renderSearchBar({ filters, onChange })

    expect(screen.getByText("金额 大于 100")).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "移除金额筛选" }))

    expect(onChange).toHaveBeenCalledWith([])
  })
})

const dateEntity = {
  fields: [{ name: "dueDate", label: "截止时间", type: "date" }],
  listView: { columns: [], filterableFields: ["dueDate"] }
} as unknown as EntityDef

const dateCapabilities: CrudFilterFieldMeta[] = [
  {
    field: "dueDate",
    operators: [{ value: "between", minValues: 2, maxValues: 2 }],
    variables: []
  }
]

describe("SearchBar 日期范围筛选", () => {
  it("提交两个日期时应用 between 条件", () => {
    const onChange = vi.fn()
    render(
      <SearchBar
        entity={dateEntity}
        filters={[]}
        onChange={onChange}
        capabilities={dateCapabilities}
      />
    )

    const input = screen.getByRole("textbox")
    fireEvent.change(input, { target: { value: "截止" } })
    fireEvent.click(screen.getByRole("button", { name: "截止时间" }))
    fireEvent.change(screen.getByLabelText("开始日期"), { target: { value: "2026-07-18" } })
    fireEvent.change(screen.getByLabelText("结束日期"), { target: { value: "2026-07-21" } })
    fireEvent.click(screen.getByRole("button", { name: "确定" }))

    expect(onChange).toHaveBeenCalledWith([
      { field: "dueDate", operator: "between", values: ["2026-07-18", "2026-07-21"] }
    ])
  })
})
