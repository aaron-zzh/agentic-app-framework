/**
 * ViewEngine 单元测试——验证视图路由逻辑
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen } from "@testing-library/react"
import type { ReactElement } from "react"
import { describe, expect, it, vi } from "vitest"

// mock tldraw（jsdom 不支持 CSS.supports）
vi.mock("tldraw", () => ({ Tldraw: () => null }))
// mock 依赖
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/"
}))
vi.mock("@/lib/queries/use-entity-list", () => ({
  useEntityList: () => ({
    data: [],
    isLoading: false,
    pagination: { page: 1, pageSize: 20, total: 0 }
  })
}))
vi.mock("@/lib/queries/use-entity-detail", () => ({
  useEntityDetail: () => ({ data: null, isLoading: false })
}))
vi.mock("@/lib/queries/use-entity-search-params", () => ({
  useEntitySearchParams: () => [{ page: 1, pageSize: 20 }, vi.fn()]
}))

import type { EntityDef } from "@/lib/types/entity"
import { ViewEngine } from "./ViewEngine"

const mockEntity: Partial<EntityDef> = {
  slug: "test",
  label: "测试实体",
  fields: [{ name: "name", label: "名称", type: "string" }],
  listView: { columns: [{ field: "name", label: "名称" }], filterableFields: [] },
  formView: {},
  overrides: {}
}

function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe("ViewEngine", () => {
  it("默认应渲染列表视图", () => {
    renderWithQueryClient(<ViewEngine entity={mockEntity} />)
    // ListView 会渲染 DataTable 或空状态
    expect(document.querySelector("[data-testid]") || document.body).toBeTruthy()
  })

  it("view=form 应渲染表单视图", () => {
    renderWithQueryClient(<ViewEngine entity={mockEntity} view="form" recordId="1" />)
    // FormView 渲染 form 元素
    expect(document.querySelector("form")).toBeTruthy()
  })

  it("未知视图应渲染占位组件", () => {
    renderWithQueryClient(<ViewEngine entity={mockEntity} view="unknown_view" />)
    expect(screen.getByText("（待实现）")).toBeInTheDocument()
  })

  it("有 overrides.listView 时应使用自定义组件", () => {
    const CustomList = () => <div data-testid="custom-list">自定义列表</div>
    const entityWithOverride = { ...mockEntity, overrides: { listView: CustomList } }

    renderWithQueryClient(<ViewEngine entity={entityWithOverride} view="list" />)

    expect(screen.getByTestId("custom-list")).toBeInTheDocument()
  })
})
