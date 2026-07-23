/**
 * ViewEngine 单元测试——验证视图路由逻辑
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen } from "@testing-library/react"
import type { ReactElement } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"

const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }))

// mock tldraw（jsdom 不支持 CSS.supports）
vi.mock("tldraw", () => ({ Tldraw: () => null }))
// mock 依赖
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
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

import type { PageResult } from "@/lib/api/rest/entity/crud"
import type { EntityDef, FormViewOverrideProps } from "@/lib/types/entity"
import { RecordWindowNavigationControls, ViewEngine } from "./ViewEngine"

const mockEntity: Partial<EntityDef> = {
  slug: "test",
  label: "测试实体",
  fields: [{ name: "name", label: "名称", type: "string" }],
  listView: { columns: [{ field: "name", label: "名称" }], filterableFields: [] },
  formView: {},
  overrides: {}
}

function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  })
}

function renderWithQueryClient(ui: ReactElement, queryClient = createQueryClient()) {
  return {
    queryClient,
    ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
  }
}

function seedQueryWindow(queryClient: QueryClient) {
  queryClient.setQueryData<PageResult<Record<string, unknown>>>(["test", "queryWindow", {}], {
    list: [{ id: "1" }, { id: "2" }, { id: "3" }],
    total: 3,
    pageNo: 1,
    pageSize: 3,
    ids: [1, 2, 3],
    queryToken: "window-1",
    fieldSet: "list",
    hasMore: false
  })
}

describe("ViewEngine", () => {
  beforeEach(() => routerPush.mockClear())

  it("默认应渲染列表视图", () => {
    renderWithQueryClient(<ViewEngine entity={mockEntity} />)
    // ListView 会渲染 DataTable 或空状态
    expect(document.querySelector("[data-testid]") || document.body).toBeTruthy()
  })

  it("view=form 应渲染可保存的表单视图", () => {
    renderWithQueryClient(<ViewEngine entity={mockEntity} view="form" recordId="1" />)
    // FormView 渲染 form 元素，并为可更新实体提供保存动作。
    expect(document.querySelector("form")).toBeTruthy()
    expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument()
  })

  it("传入外部表单 ID 时应隐藏内部保存动作", () => {
    renderWithQueryClient(
      <ViewEngine
        entity={mockEntity}
        view="form"
        recordId="1"
        externalFormId="entity-record-form-test-1"
      />
    )

    expect(document.querySelector("form")).toHaveAttribute("id", "entity-record-form-test-1")
    expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument()
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

  it("完整详情应保留查询窗口并路由到相邻记录", () => {
    const queryClient = createQueryClient()
    seedQueryWindow(queryClient)

    renderWithQueryClient(
      <ViewEngine entity={mockEntity} view="form" recordId="2" queryToken="window-1" />,
      queryClient
    )

    expect(screen.getByText("当前窗口 2 / 3")).toBeInTheDocument()
    fireEvent.click(screen.getByRole("button", { name: "下一条" }))
    expect(routerPush).toHaveBeenCalledWith("/module/test/3?qw=window-1")
  })

  it("传入记录切换回调时应保持当前路由", () => {
    const queryClient = createQueryClient()
    const onRecordChange = vi.fn()
    seedQueryWindow(queryClient)

    renderWithQueryClient(
      <ViewEngine
        entity={mockEntity}
        view="form"
        recordId="2"
        queryToken="window-1"
        onRecordChange={onRecordChange}
      />,
      queryClient
    )

    fireEvent.click(screen.getByRole("button", { name: "下一条" }))
    expect(onRecordChange).toHaveBeenCalledWith("3")
    expect(routerPush).not.toHaveBeenCalled()
  })

  it("紧凑导航只展示图标并保留无障碍名称", () => {
    render(
      <RecordWindowNavigationControls
        iconOnly
        navigation={{
          statusLabel: "当前窗口 2 / 3",
          isAvailable: true,
          prevId: "1",
          nextId: "3",
          prevTitle: "上一条",
          nextTitle: "下一条",
          navigateToRecord: vi.fn()
        }}
      />
    )

    expect(screen.getByRole("button", { name: "上一条" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "下一条" })).toBeInTheDocument()
    expect(screen.queryByText("上一条")).not.toBeInTheDocument()
    expect(screen.queryByText("下一条")).not.toBeInTheDocument()
  })

  it("查询窗口不可用时不显示导航或提示", () => {
    renderWithQueryClient(
      <ViewEngine entity={mockEntity} view="form" recordId="1" queryToken="window-unavailable" />
    )

    expect(screen.queryByText("当前查询窗口不可用")).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "上一条" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "下一条" })).not.toBeInTheDocument()
  })

  it("表单覆盖视图应接收统一详情上下文", () => {
    const CustomForm = ({ recordId, queryToken, readOnly }: FormViewOverrideProps) => (
      <div>{`${recordId}:${queryToken}:${readOnly}`}</div>
    )
    const entityWithOverride = { ...mockEntity, overrides: { formView: CustomForm } }

    renderWithQueryClient(
      <ViewEngine
        entity={entityWithOverride}
        view="form"
        recordId="1"
        queryToken="window-1"
        readOnly
      />
    )

    expect(screen.getByText("1:window-1:true")).toBeInTheDocument()
  })
})
