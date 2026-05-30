/**
 * ContextInjector.tsx 单元测试——验证上下文提取逻辑
 */

import { describe, expect, it } from "vitest"
import { buildContextPrompt, type PageContext } from "./ContextInjector"

describe("buildContextPrompt", () => {
  it("有实体时应包含模块信息", () => {
    const ctx: PageContext = { pathname: "/workspace/document", entity: "document" }

    const result = buildContextPrompt(ctx)

    expect(result).toContain("当前模块：document")
  })

  it("有视图时应包含视图信息", () => {
    const ctx: PageContext = { pathname: "/workspace/task", entity: "task", view: "kanban" }

    const result = buildContextPrompt(ctx)

    expect(result).toContain("当前视图：kanban")
  })

  it("有选中文本时应包含选中内容", () => {
    const ctx: PageContext = { pathname: "/", selectedText: "重要内容" }

    const result = buildContextPrompt(ctx)

    expect(result).toContain("用户选中文本：「重要内容」")
  })

  it("无上下文时应返回空字符串", () => {
    const ctx: PageContext = { pathname: "/" }

    const result = buildContextPrompt(ctx)

    expect(result).toBe("")
  })

  it("多个上下文应用分号连接", () => {
    const ctx: PageContext = { pathname: "/", entity: "user", view: "list", selectedText: "test" }

    const result = buildContextPrompt(ctx)

    expect(result).toContain("；")
    expect(result).toContain("当前模块：user")
    expect(result).toContain("当前视图：list")
  })
})
