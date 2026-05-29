import { render, screen, fireEvent } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { ContextChip } from "./ContextChip"
import type { ChatterDropItem } from "../types"

describe("ContextChip", () => {
  const baseItem: ChatterDropItem = { type: "doc", title: "测试文档" }

  it("渲染文档类型图标和标题", () => {
    render(<ContextChip item={baseItem} onRemove={vi.fn()} />)
    expect(screen.getByText("📄")).toBeInTheDocument()
    expect(screen.getByText("测试文档")).toBeInTheDocument()
  })

  it("点击移除按钮触发回调", () => {
    const onRemove = vi.fn()
    render(<ContextChip item={baseItem} onRemove={onRemove} />)
    fireEvent.click(screen.getByLabelText("移除"))
    expect(onRemove).toHaveBeenCalledOnce()
  })

  it("图片类型显示缩略图", () => {
    const imgItem: ChatterDropItem = {
      type: "image",
      title: "photo.png",
      thumbnailUrl: "/thumb.jpg"
    }
    render(<ContextChip item={imgItem} onRemove={vi.fn()} />)
    expect(screen.getByRole("img")).toHaveAttribute("src", "/thumb.jpg")
  })

  it("使用 summary 优先于 title", () => {
    const item: ChatterDropItem = { type: "text", title: "长标题", summary: "摘要" }
    render(<ContextChip item={item} onRemove={vi.fn()} />)
    expect(screen.getByText("摘要")).toBeInTheDocument()
  })
})
