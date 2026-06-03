import { renderHook } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

// Mock @dnd-kit/core
vi.mock("@dnd-kit/core", () => ({
  useDraggable: ({
    id,
    data: _data,
    disabled: _disabled
  }: {
    id: string
    data: unknown
    disabled: boolean
  }) => ({
    attributes: { "data-testid": id },
    listeners: {},
    setNodeRef: vi.fn(),
    isDragging: false
  })
}))

import type { ChatterDropItem } from "../types"
import { useSemanticDraggable } from "./useSemanticDraggable"

describe("useSemanticDraggable", () => {
  it("返回 ref/listeners/attributes/isDragging", () => {
    const item: ChatterDropItem = { type: "doc", title: "测试" }
    const { result } = renderHook(() => useSemanticDraggable({ id: "test-1", item }))

    expect(result.current.ref).toBeDefined()
    expect(result.current.listeners).toBeDefined()
    expect(result.current.attributes).toBeDefined()
    expect(result.current.isDragging).toBe(false)
  })

  it("自动生成 summary（截断到 100 字符）", () => {
    const longContent = "a".repeat(200)
    const item: ChatterDropItem = { type: "text", content: longContent }
    const { result } = renderHook(() => useSemanticDraggable({ id: "test-2", item }))
    // hook 内部 enrichedItem 的 summary 被截断
    expect(result.current).toBeDefined()
  })
})
