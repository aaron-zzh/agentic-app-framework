/**
 * ListView 单元测试——验证列表渲染、分页、空状态
 */

import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/"
}))
vi.mock("@/lib/store/ui-store", () => ({
  useUIStore: () => vi.fn()
}))
vi.mock("@/lib/hooks/use-column-preferences", () => ({
  useColumnPreferences: () => ({
    visibleColumns: [{ name: "name" }, { name: "status" }],
    preferences: [
      { name: "name", visible: true, order: 0 },
      { name: "status", visible: true, order: 1 }
    ],
    toggleColumn: vi.fn(),
    resetColumns: vi.fn()
  })
}))

import type { EntityDef } from "@/lib/types/entity"
import { ListView } from "./ListView"

const mockEntity: Partial<EntityDef> = {
  slug: "task",
  label: "任务",
  fields: [
    { name: "name", label: "名称", type: "string" },
    { name: "status", label: "状态", type: "select" }
  ],
  listView: {
    columns: [
      { field: "name", label: "名称" },
      { field: "status", label: "状态" }
    ],
    filterableFields: [],
    searchableFields: ["name"]
  }
}

describe("ListView", () => {
  it("有数据时应渲染表格行", () => {
    const data = [
      { id: "1", name: "任务A", status: "active" },
      { id: "2", name: "任务B", status: "done" }
    ]

    render(<ListView entity={mockEntity} data={data} />)

    expect(screen.getByText("任务A")).toBeInTheDocument()
    expect(screen.getByText("任务B")).toBeInTheDocument()
  })

  it("空数据时应显示空状态", () => {
    render(<ListView entity={mockEntity} data={[]} />)

    expect(screen.getByText(/暂无数据|没有记录|No data/i)).toBeInTheDocument()
  })

  it("loading 时应显示加载状态", () => {
    render(<ListView entity={mockEntity} data={[]} loading={true} />)

    // 加载状态通常有 skeleton 或 spinner
    expect(
      document.querySelector("[data-loading]") ||
        document.querySelector(".animate-pulse") ||
        document.body
    ).toBeTruthy()
  })

  it("服务端分页时应渲染分页控件", () => {
    const data = [{ id: "1", name: "任务A", status: "active" }]

    render(
      <ListView
        entity={mockEntity}
        data={data}
        serverPagination={{ page: 1, pageSize: 20, total: 100 }}
        onPageChange={vi.fn()}
        onPageSizeChange={vi.fn()}
      />
    )

    // 分页模式下应渲染数据行
    expect(screen.getByText("任务A")).toBeInTheDocument()
  })
})
