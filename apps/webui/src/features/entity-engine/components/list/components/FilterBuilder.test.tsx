/**
 * FilterBuilder.tsx 单元测试——验证弹窗筛选草稿与确认应用
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { CrudFilterFieldMeta } from "@/lib/api/rest/crud"
import type { EntityDef } from "@/lib/types/entity"

import { FilterBuilder } from "./FilterBuilder"

const mockEntity: Partial<EntityDef> = {
  fields: [
    { name: "name", label: "名称", type: "text" },
    { name: "amount", label: "金额", type: "number" },
    { name: "enabled", label: "启用", type: "checkbox" }
  ],
  listView: { columns: ["name", "amount"], filterableFields: ["name", "amount"] }
}

const capabilities: CrudFilterFieldMeta[] = [
  { field: "name", operators: [{ value: "eq", minValues: 1, maxValues: 1 }], variables: [] },
  {
    field: "amount",
    operators: [
      { value: "eq", minValues: 1, maxValues: 1 },
      { value: "between", minValues: 2, maxValues: 2 }
    ],
    variables: []
  }
]

describe("FilterBuilder", () => {
  it("点击添加筛选仅打开弹窗，确认后才应用条件", () => {
    const onChange = vi.fn()
    render(
      <FilterBuilder
        entity={mockEntity as EntityDef}
        filters={[]}
        onChange={onChange}
        capabilities={capabilities}
      />
    )

    fireEvent.click(screen.getByText("高级搜索"))

    expect(screen.getByRole("dialog")).toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText("筛选值"), { target: { value: "MUSIC" } })
    fireEvent.click(screen.getByRole("button", { name: "应用筛选" }))

    expect(onChange).toHaveBeenCalledWith([{ field: "name", operator: "eq", values: ["MUSIC"] }])
  })

  it("服务端未声明字段能力时不渲染高级筛选器", () => {
    render(
      <FilterBuilder
        entity={mockEntity as EntityDef}
        filters={[]}
        onChange={vi.fn()}
        capabilities={[]}
      />
    )

    expect(screen.queryByText("高级搜索")).not.toBeInTheDocument()
  })

  it("between 操作符必须填写两个值后才能应用", () => {
    render(
      <FilterBuilder
        entity={mockEntity as EntityDef}
        filters={[]}
        onChange={vi.fn()}
        capabilities={capabilities}
      />
    )

    fireEvent.click(screen.getByText("高级搜索"))
    fireEvent.change(screen.getByLabelText("筛选字段"), { target: { value: "amount" } })
    fireEvent.change(screen.getByLabelText("筛选操作符"), { target: { value: "between" } })

    const applyButton = screen.getByRole("button", { name: "应用筛选" })
    fireEvent.change(screen.getByLabelText("筛选起始值"), { target: { value: "1" } })
    expect(applyButton).toBeDisabled()

    fireEvent.change(screen.getByLabelText("筛选结束值"), { target: { value: "10" } })
    expect(applyButton).not.toBeDisabled()
  })

  it("字段操作符仅展示服务端声明的能力", () => {
    render(
      <FilterBuilder
        entity={mockEntity as EntityDef}
        filters={[]}
        onChange={vi.fn()}
        capabilities={capabilities}
      />
    )

    fireEvent.click(screen.getByText("高级搜索"))
    fireEvent.change(screen.getByLabelText("筛选字段"), { target: { value: "amount" } })

    expect(screen.getByText("介于")).toBeInTheDocument()
    expect(screen.queryByText("大于等于")).not.toBeInTheDocument()
  })

  it("选项字段的 between 操作符收集起始值和结束值", () => {
    const onChange = vi.fn()
    const selectEntity: Partial<EntityDef> = {
      fields: [
        {
          name: "status",
          label: "状态",
          type: "select",
          options: [
            { label: "待处理", value: "pending" },
            { label: "已完成", value: "done" }
          ]
        }
      ],
      listView: { columns: ["status"], filterableFields: ["status"] }
    }
    const selectCapabilities: CrudFilterFieldMeta[] = [
      {
        field: "status",
        operators: [{ value: "between", minValues: 2, maxValues: 2 }],
        variables: []
      }
    ]

    render(
      <FilterBuilder
        entity={selectEntity as EntityDef}
        filters={[]}
        onChange={onChange}
        capabilities={selectCapabilities}
      />
    )

    fireEvent.click(screen.getByText("高级搜索"))
    fireEvent.change(screen.getByLabelText("筛选起始值"), { target: { value: "pending" } })
    expect(screen.getByRole("button", { name: "应用筛选" })).toBeDisabled()

    fireEvent.change(screen.getByLabelText("筛选结束值"), { target: { value: "done" } })
    fireEvent.click(screen.getByRole("button", { name: "应用筛选" }))

    expect(onChange).toHaveBeenCalledWith([
      { field: "status", operator: "between", values: ["pending", "done"] }
    ])
  })

  it("日期字段保留日期选择器并以原始 token 提交中文变量预设", () => {
    const onChange = vi.fn()
    const dateEntity: Partial<EntityDef> = {
      fields: [{ name: "dueDate", label: "截止时间", type: "date" }],
      listView: { columns: ["dueDate"], filterableFields: ["dueDate"] }
    }
    const dateCapabilities: CrudFilterFieldMeta[] = [
      {
        field: "dueDate",
        operators: [{ value: "between", minValues: 2, maxValues: 2 }],
        variables: ["$now", "$todayStart", "$tomorrowStart", "$nowPlus3Days"]
      }
    ]

    render(
      <FilterBuilder
        entity={dateEntity as EntityDef}
        filters={[]}
        onChange={onChange}
        capabilities={dateCapabilities}
      />
    )

    fireEvent.click(screen.getByText("高级搜索"))
    expect(screen.getByLabelText("筛选起始值")).toHaveAttribute("type", "date")
    expect(screen.getByLabelText("筛选结束值")).toHaveAttribute("type", "date")
    expect(screen.getAllByRole("option", { name: "当前时间" })).toHaveLength(2)
    expect(screen.getAllByRole("option", { name: "今日开始" })).toHaveLength(2)

    fireEvent.change(screen.getByLabelText("起始日期变量"), { target: { value: "$todayStart" } })
    expect(screen.getByRole("button", { name: "应用筛选" })).not.toBeDisabled()

    fireEvent.change(screen.getByLabelText("结束日期变量"), { target: { value: "$nowPlus3Days" } })
    fireEvent.click(screen.getByRole("button", { name: "应用筛选" }))

    expect(onChange).toHaveBeenCalledWith([
      { field: "dueDate", operator: "between", values: ["$todayStart", "$nowPlus3Days"] }
    ])
  })
})
