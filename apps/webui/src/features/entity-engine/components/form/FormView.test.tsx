/**
 * FormView 单元测试——验证表单渲染和提交
 */

import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

// mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/"
}))

import type { EntityDef } from "@/lib/types/entity"
import { FormView } from "./FormView"

const mockEntity: Partial<EntityDef> = {
  slug: "user",
  label: "用户",
  fields: [
    { name: "age", label: "年龄", type: "number" },
    {
      name: "status",
      label: "状态",
      type: "select",
      options: [
        { label: "活跃", value: "active" },
        { label: "禁用", value: "disabled" }
      ]
    }
  ],
  formView: {},
  listView: { columns: [] }
}

describe("FormView", () => {
  it("应渲染已注册类型的字段", () => {
    render(<FormView entity={mockEntity} />)

    expect(screen.getByLabelText("年龄")).toBeInTheDocument()
    expect(screen.getByLabelText("状态")).toBeInTheDocument()
  })

  it("loading 时应显示骨架屏", () => {
    render(<FormView entity={mockEntity} loading={true} />)

    expect(screen.queryByLabelText("年龄")).not.toBeInTheDocument()
  })

  it("有 data 时应填充默认值", () => {
    render(<FormView entity={mockEntity} data={{ age: 25, status: "active" }} />)

    expect(screen.getByDisplayValue("25")).toBeInTheDocument()
  })

  it("data 异步到达（挂载后从空变为有值）时应重新填充表单", () => {
    const { rerender } = render(<FormView entity={mockEntity} data={undefined} loading={true} />)

    // 首次挂载时数据未到达，渲染骨架屏
    expect(screen.queryByLabelText("年龄")).not.toBeInTheDocument()

    // 数据异步加载完成，loading 结束、data 到达
    rerender(<FormView entity={mockEntity} data={{ age: 25, status: "active" }} loading={false} />)

    expect(screen.getByDisplayValue("25")).toBeInTheDocument()
  })

  it("提交时应调用 onSubmit", async () => {
    const onSubmit = vi.fn()
    render(
      <FormView entity={mockEntity} data={{ age: 18, status: "active" }} onSubmit={onSubmit} />
    )

    const form = document.querySelector("form")
    if (!form) throw new Error("form not found")
    fireEvent.submit(form)

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalled()
    })
  })

  it("未传 onSubmit（无更新权限）时不应渲染保存按钮", () => {
    render(<FormView entity={mockEntity} data={{ age: 18, status: "active" }} />)

    expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument()
  })

  it("传入 onSubmit（有更新权限）时应渲染保存按钮", () => {
    render(<FormView entity={mockEntity} data={{ age: 18, status: "active" }} onSubmit={vi.fn()} />)

    expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument()
  })

  it("hidden 字段不应渲染", () => {
    const entityWithHidden = {
      ...mockEntity,
      fields: [
        ...mockEntity.fields,
        { name: "deleted", label: "已删除", type: "number", hidden: true }
      ]
    }
    render(<FormView entity={entityWithHidden} />)

    expect(screen.queryByLabelText("已删除")).not.toBeInTheDocument()
  })
})
