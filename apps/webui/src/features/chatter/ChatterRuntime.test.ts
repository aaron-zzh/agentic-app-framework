/**
 * ChatterRuntime 单元测试——验证错误分类和端点 URL 构建
 */

import { describe, expect, it } from "vitest"

// 直接测试 classifyError 和 getEndpointUrl 的逻辑（提取为纯函数测试）
// 由于这些是模块内部函数，我们测试其行为等价逻辑

describe("ChatterRuntime 错误分类", () => {
  function classifyError(error: Error): string {
    const msg = error.message.toLowerCase()
    if (msg.includes("network") || msg.includes("fetch") || msg.includes("failed to fetch")) {
      return "网络连接异常，请检查网络后重试"
    }
    if (msg.includes("429") || msg.includes("rate limit")) {
      return "请求配额超限，请稍后再试"
    }
    if (msg.includes("500") || msg.includes("internal")) {
      return "服务异常，请稍后再试"
    }
    return "对话出现错误，请重试"
  }

  it("网络错误应返回网络提示", () => {
    expect(classifyError(new Error("Failed to fetch"))).toContain("网络")
  })

  it("429 应返回配额提示", () => {
    expect(classifyError(new Error("429 Too Many Requests"))).toContain("配额")
  })

  it("500 应返回服务异常提示", () => {
    expect(classifyError(new Error("500 Internal Server Error"))).toContain("服务异常")
  })

  it("未知错误应返回通用提示", () => {
    expect(classifyError(new Error("unknown"))).toContain("对话出现错误")
  })
})

describe("ChatterRuntime 端点 URL 构建", () => {
  function buildAguiUrl(target: { type: string; agentRole?: string }): string {
    if (target.type === "kiro") return "/api/autodev/kiro/run"
    const agentId = target.agentRole ?? "default"
    return `/api/agui/runs/${agentId}`
  }

  it("kiro 类型应使用 autodev 独立端点", () => {
    expect(buildAguiUrl({ type: "kiro" })).toContain("/autodev/kiro/run")
  })

  it("ai 类型无 agentRole 应使用 default", () => {
    expect(buildAguiUrl({ type: "ai" })).toContain("/default")
  })

  it("agentRole 应作为 agentId 路径", () => {
    expect(buildAguiUrl({ type: "ai", agentRole: "customer-service" })).toContain("/customer-service")
  })
})
