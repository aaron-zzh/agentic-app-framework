import { render, screen } from "@testing-library/react"
import type { ReactNode } from "react"
import { describe, expect, it, vi } from "vitest"

vi.mock("@/components/common/CustomBreadcrumbs", () => ({
  CustomBreadcrumbs: ({
    links,
    action
  }: {
    links: Array<{ name: string }>
    action?: ReactNode
  }) => (
    <div data-testid="breadcrumbs">
      {links.map((link) => link.name).join("/")}
      {action}
    </div>
  )
}))

vi.mock("@/components/common/PageContainer", () => ({
  PageContainer: ({ children, maxWidth }: { children: ReactNode; maxWidth?: string }) => (
    <div data-testid="page-container" data-max-width={maxWidth}>
      {children}
    </div>
  )
}))

vi.mock("@/features/entity-engine/components", () => ({
  RecordWindowNavigationControls: ({ iconOnly }: { iconOnly?: boolean }) => (
    <div data-testid="record-window-controls" data-icon-only={String(iconOnly)} />
  ),
  useRecordWindowNavigation: () => ({ isAvailable: true }),
  ViewEngine: ({
    recordId,
    queryToken,
    externalFormId,
    showRecordWindowPager
  }: {
    recordId?: string
    queryToken?: string
    externalFormId?: string
    showRecordWindowPager?: boolean
  }) => (
    <div
      data-testid="view-engine"
      data-external-form-id={externalFormId}
      data-show-record-window-pager={String(showRecordWindowPager)}
    >
      {`${recordId}:${queryToken}`}
    </div>
  )
}))

import type { EntityDef } from "@/features/entity-engine/types"
import { EntityRecordView } from "./entity-record-view"

const entity = {
  slug: "todo",
  label: "待办",
  fields: [],
  formView: {},
  listView: { columns: [] }
} as EntityDef

describe("EntityRecordView", () => {
  it("使用 lg 内容容器承载记录详情表单", () => {
    render(<EntityRecordView entity={entity} recordId="2" queryToken="window-token" />)

    const breadcrumbs = screen.getByTestId("breadcrumbs")
    expect(breadcrumbs).toHaveTextContent("首页/待办/详情")
    expect(screen.getByRole("button", { name: "保存" })).toHaveAttribute(
      "form",
      "entity-record-form-todo-2"
    )
    expect(screen.getByTestId("record-window-controls")).toHaveAttribute("data-icon-only", "true")
    expect(screen.getByTestId("page-container")).toHaveAttribute("data-max-width", "lg")
    expect(screen.getByTestId("view-engine")).toHaveTextContent("2:window-token")
    expect(screen.getByTestId("view-engine")).toHaveAttribute(
      "data-external-form-id",
      "entity-record-form-todo-2"
    )
    expect(screen.getByTestId("view-engine")).toHaveAttribute(
      "data-show-record-window-pager",
      "false"
    )
  })
})
