import { render, screen } from "@testing-library/react"
import type { ReactNode } from "react"
import { describe, expect, it, vi } from "vitest"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() })
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

vi.mock("@/features/entity-engine/components/form/FormView", () => ({
  FormView: ({ entity, mode }: { entity: { fields: Array<{ name: string }> }; mode?: string }) => (
    <div data-testid="form-fields">{`${mode}:${entity.fields.map((field) => field.name).join(",")}`}</div>
  )
}))

vi.mock("@/features/entity-engine/hooks/use-resolved-entity", () => ({
  useResolvedEntity: <T,>(entity: T) => entity
}))

vi.mock("@/lib/api/rest/crud", () => ({
  fromEntityDef: vi.fn(),
  useCrudCreate: () => ({ isPending: false, mutate: vi.fn() })
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
})
