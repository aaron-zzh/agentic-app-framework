/**
 * FilterBar.tsx 单元测试——验证默认筛选字段与视图设置覆盖
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { EntityDef } from "@/lib/types/entity"

import { FilterBar } from "./FilterBar"

const entity = {
  fields: [
    { name: "category", label: "分类", type: "text" },
    { name: "status", label: "状态", type: "text" }
  ],
  listView: { columns: [], filterFields: ["status"] }
} as unknown as EntityDef

describe("FilterBar", () => {
  it("默认展示 EntityDef 声明的筛选字段", () => {
    render(<FilterBar entity={entity} filters={[]} onChange={vi.fn()} />)

    expect(screen.getByPlaceholderText("状态")).toBeInTheDocument()
    expect(screen.queryByPlaceholderText("分类")).not.toBeInTheDocument()
  })

  it("视图设置的筛选字段覆盖 EntityDef 默认值", () => {
    render(
      <FilterBar
        entity={entity}
        filters={[]}
        onChange={vi.fn()}
        viewSettings={{ filterFields: ["category"] }}
      />
    )

    expect(screen.getByPlaceholderText("分类")).toBeInTheDocument()
    expect(screen.queryByPlaceholderText("状态")).not.toBeInTheDocument()
  })

  it("视图设置明确关闭所有筛选字段时不回退默认值", () => {
    const { container } = render(
      <FilterBar
        entity={entity}
        filters={[]}
        onChange={vi.fn()}
        viewSettings={{ filterFields: [] }}
      />
    )

    expect(container).toBeEmptyDOMElement()
  })

  it("高级搜索入口位于筛选栏尾部", () => {
    render(
      <FilterBar
        entity={entity}
        filters={[]}
        onChange={vi.fn()}
        trailingAction={<button type="button">高级搜索</button>}
      />
    )

    const action = screen.getByRole("button", { name: "高级搜索" })
    expect((action.parentElement as HTMLElement).lastElementChild).toBe(action)
  })
})

const dateEntity = {
  fields: [{ name: "dueDate", label: "截止时间", type: "date" }],
  listView: { columns: [], filterFields: ["dueDate"] }
} as unknown as EntityDef

describe("日期快捷范围筛选", () => {
  it("仅填写起始日期时应用 gte 条件", () => {
    const onChange = vi.fn()
    render(<FilterBar entity={dateEntity} filters={[]} onChange={onChange} />)

    fireEvent.change(screen.getByLabelText("截止时间起始日期"), {
      target: { value: "2026-07-01" }
    })

    expect(onChange).toHaveBeenCalledWith([
      { field: "dueDate", operator: "gte", values: ["2026-07-01"] }
    ])
  })

  it("仅填写结束日期时应用 lte 条件", () => {
    const onChange = vi.fn()
    render(<FilterBar entity={dateEntity} filters={[]} onChange={onChange} />)

    fireEvent.change(screen.getByLabelText("截止时间结束日期"), {
      target: { value: "2026-07-31" }
    })

    expect(onChange).toHaveBeenCalledWith([
      { field: "dueDate", operator: "lte", values: ["2026-07-31"] }
    ])
  })

  it("同时填写起止日期时应用 between 条件", () => {
    const onChange = vi.fn()
    render(
      <FilterBar
        entity={dateEntity}
        filters={[{ field: "dueDate", operator: "gte", values: ["2026-07-01"] }]}
        onChange={onChange}
      />
    )

    fireEvent.change(screen.getByLabelText("截止时间结束日期"), {
      target: { value: "2026-07-31" }
    })

    expect(onChange).toHaveBeenCalledWith([
      { field: "dueDate", operator: "between", values: ["2026-07-01", "2026-07-31"] }
    ])
  })
})
