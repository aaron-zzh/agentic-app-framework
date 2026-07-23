/**
 * RelationshipPicker 单元测试
 * @author AaronZZH & Kiro
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

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
  recordRecent: vi.fn(),
  setQuery: vi.fn()
}))

vi.mock("@/lib/hooks/use-relationship-picker", () => ({
  useRelationshipPicker: () => ({
    query: "",
    setQuery: pickerState.setQuery,
    displayOptions: [
      { id: "8", label: "王五", imageUrl: "https://cdn.example/wang.png" },
      { id: "9", label: "李四" }
    ],
    loading: false,
    recordRecent: pickerState.recordRecent
  })
}))

import { RelationshipPicker } from "./relationship-picker"

const field = {
  type: "relationship" as const,
  name: "participants",
  label: "参与人",
  relationTo: "system.user",
  hasMany: true
}

describe("RelationshipPicker", () => {
  it("多选空白 ID 应视为未选择", () => {
    render(
      <RelationshipPicker
        name="participants"
        value={[""]}
        onChange={vi.fn()}
        field={field}
        multiple
        placeholder="搜索用户…"
      />
    )

    expect(screen.getByRole("button", { name: "选择participants" })).toHaveTextContent("搜索用户")
    expect(screen.queryByText("?")).not.toBeInTheDocument()
  })

  it("多选已选项在选择输入框内展示，并可通过下拉候选移除", () => {
    const onChange = vi.fn()
    render(
      <RelationshipPicker
        name="participants"
        value={["8"]}
        onChange={onChange}
        field={field}
        multiple
        selectedOptions={[{ id: "8", label: "王五", imageUrl: "https://cdn.example/wang.png" }]}
      />
    )

    const trigger = screen.getByRole("button", { name: "选择participants" })
    expect(trigger).toHaveTextContent("王五")
    expect(screen.queryByRole("button", { name: "移除 王五" })).not.toBeInTheDocument()

    fireEvent.click(trigger)
    const wangOptions = screen.getAllByText("王五")
    fireEvent.click(wangOptions[wangOptions.length - 1])
    expect(onChange).toHaveBeenCalledWith([])
  })

  it("单选已选用户在选择输入框内展示头像与名称", () => {
    render(
      <RelationshipPicker
        name="assignee"
        value="8"
        onChange={vi.fn()}
        field={{ ...field, hasMany: false, name: "assignee", label: "执行人" }}
        selectedOptions={[{ id: "8", label: "王五", imageUrl: "https://cdn.example/wang.png" }]}
      />
    )

    expect(screen.getByRole("button", { name: "选择assignee" })).toHaveTextContent("王五")
  })

  it("候选项点击可添加或取消已选用户", () => {
    const onChange = vi.fn()
    render(
      <RelationshipPicker
        name="participants"
        value={["8"]}
        onChange={onChange}
        field={field}
        multiple
        selectedOptions={[{ id: "8", label: "王五" }]}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "选择participants" }))
    fireEvent.click(screen.getByText("李四"))
    expect(onChange).toHaveBeenCalledWith(["8", "9"])

    const wangOptions = screen.getAllByText("王五")
    fireEvent.click(wangOptions[wangOptions.length - 1])
    expect(onChange).toHaveBeenLastCalledWith([])
  })
})
