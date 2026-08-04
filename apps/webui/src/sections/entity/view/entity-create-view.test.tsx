import { fireEvent, render, screen } from "@testing-library/react"
import type { ReactNode } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"

const { mockBack, mockMutate, mockReplace } = vi.hoisted(() => ({
  mockBack: vi.fn(),
  mockMutate: vi.fn(),
  mockReplace: vi.fn()
}))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace })
}))

vi.mock("@/components/common/CustomBreadcrumbs", () => ({
  CustomBreadcrumbs: () => null
}))

vi.mock("@/components/common/PageContainer", () => ({
  PageContainer: ({ children, maxWidth }: { children: ReactNode; maxWidth?: string }) => (
    <div data-testid="page-container" data-max-width={maxWidth}>
      {children}
    </div>
  )
}))

vi.mock("@/components/ui/dialog", () => ({
  Dialog: ({
    children,
    onOpenChange
  }: {
    children: ReactNode
    onOpenChange: (open: boolean) => void
  }) => (
    <div role="dialog">
      {children}
      <button type="button" onClick={() => onOpenChange(false)}>
        关闭弹窗
      </button>
    </div>
  ),
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <header>{children}</header>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>
}))

vi.mock("@/features/entity-engine/components/form/FormView", () => ({
  FormView: ({
    entity,
    mode,
    onSubmit
  }: {
    entity: { fields: Array<{ name: string }> }
    mode?: string
    onSubmit?: (values: Record<string, unknown>) => void
  }) => (
    <div>
      <div data-testid="form-fields">{`${mode}:${entity.fields.map((field) => field.name).join(",")}`}</div>
      <button type="button" onClick={() => onSubmit?.({ title: "新待办" })}>
        提交创建
      </button>
    </div>
  )
}))

vi.mock("@/features/entity-engine/hooks/use-resolved-entity", () => ({
  useResolvedEntity: <T,>(entity: T) => entity
}))

vi.mock("@/lib/api/rest/crud", () => ({
  fromEntityDef: vi.fn(),
  useCrudCreate: () => ({ isPending: false, mutate: mockMutate })
}))

import type { EntityDef } from "@/features/entity-engine/types"
import { EntityCreateView } from "./entity-create-view"

function createEntity(slug: string): EntityDef {
  return {
    slug,
    label: "测试实体",
    fields: [
      { type: "text", name: "title", label: "标题" },
      {
        type: "recordReference",
        name: "source",
        label: "关联来源（可选）",
        writeKey: "source",
        idValueType: "number"
      },
      { type: "relationship", name: "assignee", label: "执行人", relationTo: "system.user" }
    ],
    formView: {},
    listView: { columns: [] }
  }
}

describe("EntityCreateView", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("统一以创建模式将 Todo 字段交给 FormView", () => {
    render(<EntityCreateView entity={createEntity("todo")} />)

    expect(screen.getByTestId("form-fields")).toHaveTextContent("create:title,source,assignee")
  })

  it("使用 lg 内容容器承载创建表单卡片", () => {
    render(<EntityCreateView entity={createEntity("todo")} />)

    expect(screen.getByTestId("page-container")).toHaveAttribute("data-max-width", "lg")
  })

  it("所有实体均复用相同的创建表单入口", () => {
    render(<EntityCreateView entity={createEntity("custom")} />)

    expect(screen.getByTestId("form-fields")).toHaveTextContent("create:title,source,assignee")
  })

  it("拦截路由使用弹窗承载创建表单，关闭时返回来源页面", () => {
    render(<EntityCreateView entity={createEntity("todo")} presentation="dialog" />)

    expect(screen.getByRole("dialog")).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: "新建测试实体" })).toBeInTheDocument()
    expect(screen.queryByTestId("page-container")).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "关闭弹窗" }))
    expect(mockBack).toHaveBeenCalledOnce()
  })

  it("完整页面创建成功后返回实体列表", () => {
    mockMutate.mockImplementationOnce(
      (_values: Record<string, unknown>, options: { onSuccess: () => void }) => options.onSuccess()
    )
    render(<EntityCreateView entity={createEntity("todo")} />)

    fireEvent.click(screen.getByRole("button", { name: "提交创建" }))

    expect(mockReplace).toHaveBeenCalledWith("/module/todo")
    expect(mockBack).not.toHaveBeenCalled()
  })

  it("弹窗创建成功后关闭并返回列表", () => {
    mockMutate.mockImplementationOnce(
      (_values: Record<string, unknown>, options: { onSuccess: () => void }) => options.onSuccess()
    )
    render(<EntityCreateView entity={createEntity("todo")} presentation="dialog" />)

    fireEvent.click(screen.getByRole("button", { name: "提交创建" }))

    expect(mockBack).toHaveBeenCalledOnce()
    expect(mockReplace).not.toHaveBeenCalled()
  })
})
