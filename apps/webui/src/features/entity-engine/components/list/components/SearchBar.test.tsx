/**
 * SearchBar.tsx 单元测试——验证搜索条件构建逻辑
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import { SearchBar } from "./SearchBar"

const mockEntity = {
  fields: [
    { name: "name", label: "名称", type: "string" },
    { name: "status", label: "状态", type: "select", options: ["active", "inactive"] },
    { name: "createdAt", label: "创建时间", type: "datetime" }
  ],
  listView: { filterableFields: ["name", "status"] }
} as any

describe("SearchBar", () => {
  it("应渲染搜索输入框", () => {
    render(<SearchBar entity={mockEntity} filters={[]} onChange={vi.fn()} />)

    expect(screen.getByRole("textbox")).toBeInTheDocument()
  })

  it("输入文字时应显示字段建议", () => {
    render(<SearchBar entity={mockEntity} filters={[]} onChange={vi.fn()} />)

    const input = screen.getByRole("textbox")
    fireEvent.change(input, { target: { value: "名" } })

    // 应显示匹配的字段
    expect(screen.getByText("名称")).toBeInTheDocument()
  })

  it("已有 filters 时应显示 tag", () => {
    const filters = [{ field: "name", operator: "contains", value: "张" }]
    render(<SearchBar entity={mockEntity} filters={filters} onChange={vi.fn()} />)

    expect(screen.getByText(/张/)).toBeInTheDocument()
  })
})
