/**
 * EntityRecordReferencePicker 单元测试
 * @author AaronZZH & Kiro
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

vi.stubGlobal(
  "ResizeObserver",
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
)

Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
  configurable: true,
  value: vi.fn()
})

const pickerState = vi.hoisted(() => ({
  endpoint: undefined as string | undefined,
  recordRecent: vi.fn(),
  setQuery: vi.fn()
}))

vi.mock("@/lib/hooks/use-relationship-picker", () => ({
  useRelationshipPicker: (endpoint?: string) => {
    pickerState.endpoint = endpoint
    return {
      query: "",
      setQuery: pickerState.setQuery,
      displayOptions: [{ id: "42", label: "王五", imageUrl: "https://cdn.example/avatar.png" }],
      loading: false,
      recordRecent: pickerState.recordRecent
    }
  }
}))

import { entityRegistry } from "@/lib/modules/entity-registry"
import type { EntityDef } from "@/lib/types/entity"
import { EntityRecordReferencePicker } from "./entity-record-reference-picker"

const documentEntity: EntityDef = {
  kind: "code",
  resource: "content.document",
  slug: "document",
  label: "文档",
  apiPath: "/documents",
  referenceable: true,
  fields: [],
  listView: { columns: [] }
}

const todoEntity: EntityDef = {
  kind: "code",
  resource: "system.todo",
  slug: "todo",
  label: "待办",
  apiPath: "/todos",
  referenceable: true,
  fields: [],
  listView: { columns: [] }
}

beforeEach(() => {
  pickerState.endpoint = undefined
  pickerState.recordRecent.mockClear()
  pickerState.setQuery.mockClear()
  entityRegistry.replaceAll([documentEntity, todoEntity], [])
})

afterEach(() => entityRegistry.clear())

describe("EntityRecordReferencePicker", () => {
  it("自由模式仅显示筛选后的可引用实体", () => {
    render(
      <EntityRecordReferencePicker
        onChange={vi.fn()}
        allowedEntitySlugs={["document", "todo"]}
        excludeEntitySlugs={["todo"]}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "选择关联来源" }))
    fireEvent.click(screen.getByRole("combobox", { name: "选择引用实体" }))
    expect(screen.getByRole("option", { name: "文档" })).toBeInTheDocument()
    expect(screen.queryByRole("option", { name: "待办" })).not.toBeInTheDocument()
  })

  it("固定模式隐藏实体选择并使用固定实体的可信 options 路径", () => {
    render(<EntityRecordReferencePicker entitySlug="document" onChange={vi.fn()} />)

    expect(screen.queryByRole("combobox", { name: "选择引用实体" })).not.toBeInTheDocument()
    expect(pickerState.endpoint).toBe("/documents/_options")
  })

  it("自由模式重选实体时清除旧引用", () => {
    const onChange = vi.fn()
    render(
      <EntityRecordReferencePicker
        value={{ resource: "system.todo", entitySlug: "todo", id: "7", label: "旧待办" }}
        onChange={onChange}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "待办 · 旧待办" }))
    fireEvent.click(screen.getByRole("combobox", { name: "选择引用实体" }))
    const documentOption = screen.getByRole("option", { name: "文档" })
    fireEvent.mouseMove(documentOption)
    fireEvent.pointerDown(documentOption)
    fireEvent.click(documentOption)

    expect(onChange).toHaveBeenCalledWith(undefined)
    expect(pickerState.setQuery).toHaveBeenCalledWith("")
  })

  it("已选引用仅为注册表可解析实体提供详情跳转", () => {
    render(
      <EntityRecordReferencePicker
        value={{ resource: "content.document", entitySlug: "document", id: "a/b", label: "设计稿" }}
        onChange={vi.fn()}
      />
    )

    expect(screen.getByRole("button", { name: "文档 · 设计稿" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "打开文档 · 设计稿详情" })).toHaveAttribute(
      "href",
      expect.stringContaining("a%2Fb")
    )
  })

  it("自由模式在同一浮层中选择实体后可直接选择记录", () => {
    const onChange = vi.fn()
    render(<EntityRecordReferencePicker onChange={onChange} />)

    fireEvent.click(screen.getByRole("button", { name: "选择关联来源" }))
    fireEvent.click(screen.getByRole("combobox", { name: "选择引用实体" }))
    const documentOption = screen.getByRole("option", { name: "文档" })
    fireEvent.pointerDown(documentOption)
    fireEvent.click(documentOption)
    expect(screen.getByRole("combobox", { name: "选择引用实体" })).toHaveTextContent("文档")
    fireEvent.click(screen.getByText("王五"))

    expect(onChange).toHaveBeenCalledWith({
      resource: "content.document",
      entitySlug: "document",
      id: "42",
      label: "王五",
      imageUrl: "https://cdn.example/avatar.png"
    })
  })

  it("选择记录时输出资源、实体与记录展示引用", () => {
    const onChange = vi.fn()
    render(<EntityRecordReferencePicker entitySlug="document" onChange={onChange} />)

    fireEvent.click(screen.getByRole("button", { name: "选择关联来源" }))
    fireEvent.click(screen.getByText("王五"))

    expect(onChange).toHaveBeenCalledWith({
      resource: "content.document",
      entitySlug: "document",
      id: "42",
      label: "王五",
      imageUrl: "https://cdn.example/avatar.png"
    })
  })
})
