/**
 * FormView 单元测试——验证表单渲染和提交
 */

import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/"
}))

vi.mock("@/components/form/entity-record-reference-picker", () => ({
  EntityRecordReferencePicker: ({
    onChange,
    disabled
  }: {
    onChange: (value: { resource: string; entitySlug: string; id: string; label: string }) => void
    disabled?: boolean
  }) => (
    <button
      type="button"
      disabled={disabled}
      onClick={() =>
        onChange({
          resource: "system.document",
          entitySlug: "document",
          id: "7",
          label: "需求文档"
        })
      }
    >
      选择关联记录
    </button>
  )
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

const recordReferenceEntity: Partial<EntityDef> = {
  slug: "todo",
  label: "待办",
  fields: [
    { name: "title", label: "标题", type: "text" },
    {
      type: "recordReference",
      name: "sourceReference",
      label: "关联来源（可选）",
      writeKey: "source",
      idValueType: "number",
      displayModes: ["create"]
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

  it("根据表单容器动态调整内边距", () => {
    render(<FormView entity={mockEntity} />)

    expect(document.querySelector("form")).toHaveClass("p-4", "@sm:p-5", "@lg:p-6")
  })

  it("无匹配角色时隐藏字段且不提交其默认值", async () => {
    const onSubmit = vi.fn()
    const roleRestrictedEntity: Partial<EntityDef> = {
      ...mockEntity,
      fields: [
        {
          name: "assignee",
          label: "执行人",
          type: "text",
          visibleRoles: ["org_admin"]
        }
      ]
    }
    render(<FormView entity={roleRestrictedEntity} data={{ assignee: "7" }} onSubmit={onSubmit} />)

    expect(screen.queryByLabelText("执行人")).not.toBeInTheDocument()
    const form = document.querySelector("form")
    if (!form) throw new Error("form not found")
    fireEvent.submit(form)

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({})
    })
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

  it("创建模式应将记录引用映射为 DTO 字段", async () => {
    const onSubmit = vi.fn()
    render(<FormView entity={recordReferenceEntity} mode="create" onSubmit={onSubmit} />)

    fireEvent.click(screen.getByRole("button", { name: "选择关联记录" }))
    const form = document.querySelector("form")
    if (!form) throw new Error("form not found")
    fireEvent.submit(form)

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({ source: { resource: "system.document", id: 7 } })
    })
  })

  it("未选择记录引用时应显式提交 source:null", async () => {
    const onSubmit = vi.fn()
    render(<FormView entity={recordReferenceEntity} mode="create" onSubmit={onSubmit} />)

    const form = document.querySelector("form")
    if (!form) throw new Error("form not found")
    fireEvent.submit(form)

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ source: null }))
  })

  it("非 MANUAL 来源的记录引用应为只读", () => {
    const entityWithSource = {
      ...recordReferenceEntity,
      fields: [
        {
          type: "recordReference" as const,
          name: "source",
          label: "关联来源（可选）",
          writeKey: "source",
          idValueType: "number" as const,
          readOnlyWhen: "$record.sourceType !== 'manual'"
        }
      ]
    }
    render(
      <FormView
        entity={entityWithSource}
        data={{ sourceType: "comment", source: { resource: "system.document", id: 7 } }}
      />
    )

    expect(screen.getByRole("button", { name: "选择关联记录" })).toBeDisabled()
  })

  it("编辑模式不应渲染仅创建时显示的记录引用", () => {
    render(<FormView entity={recordReferenceEntity} mode="edit" />)

    expect(screen.queryByRole("button", { name: "选择关联记录" })).not.toBeInTheDocument()
  })

  it("未传 onSubmit（无更新权限）时不应渲染保存按钮", () => {
    render(<FormView entity={mockEntity} data={{ age: 18, status: "active" }} />)

    expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument()
  })

  it("传入 onSubmit（有更新权限）时应渲染保存按钮", () => {
    render(<FormView entity={mockEntity} data={{ age: 18, status: "active" }} onSubmit={vi.fn()} />)

    expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument()
  })

  it("外置保存动作时应提供表单 ID 并隐藏底部重复按钮", async () => {
    const onSubmit = vi.fn()
    render(
      <FormView
        entity={mockEntity}
        data={{ age: 18, status: "active" }}
        onSubmit={onSubmit}
        externalFormId="entity-record-form-user-1"
      />
    )

    const form = document.querySelector("form")
    expect(form).toHaveAttribute("id", "entity-record-form-user-1")
    expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument()
    if (!form) throw new Error("form not found")
    fireEvent.submit(form)

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
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

  it("来源使用单一引用对象展示", () => {
    const entityWithSource: Partial<EntityDef> = {
      ...mockEntity,
      fields: [
        ...mockEntity.fields,
        {
          type: "recordReference",
          name: "source",
          label: "关联来源（可选）",
          writeKey: "source",
          idValueType: "number"
        }
      ]
    }
    render(
      <FormView
        entity={entityWithSource}
        data={{ age: 18, source: { resource: "system.document", id: 7 } }}
      />
    )

    expect(screen.getByRole("button", { name: "选择关联记录" })).toBeInTheDocument()
  })

  it("审计用户引用应显示 ResourceRef 的名称与头像", () => {
    const entityWithAudit: Partial<EntityDef> = {
      ...mockEntity,
      fields: [
        ...mockEntity.fields,
        {
          type: "relationship",
          name: "createBy",
          label: "创建人",
          relationTo: "system.user",
          readOnly: true
        }
      ]
    }
    render(
      <FormView
        entity={entityWithAudit}
        data={{
          age: 18,
          status: "active",
          createBy: { id: "1", label: "管理员", imageUrl: "https://cdn.example/admin.png" }
        }}
      />
    )

    expect(screen.getByText("管理员")).toBeInTheDocument()
    expect(document.querySelector('[data-slot="avatar"]')).toBeInTheDocument()
  })
})
