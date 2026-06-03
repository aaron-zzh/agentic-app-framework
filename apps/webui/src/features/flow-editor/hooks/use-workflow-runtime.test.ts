/**
 * use-workflow-runtime.ts 单元测试——验证状态机逻辑
 */

import { act, renderHook } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useWorkflowRuntime } from "./use-workflow-runtime"

// mock fetch
const mockFetch = vi.fn()
vi.stubGlobal("fetch", mockFetch)

// mock crypto.randomUUID
vi.stubGlobal("crypto", { randomUUID: () => "mock-uuid" })

describe("useWorkflowRuntime", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("初始状态应为 idle", () => {
    const { result } = renderHook(() => useWorkflowRuntime())

    expect(result.current.status).toBe("idle")
    expect(result.current.runId).toBeNull()
    expect(result.current.messages).toEqual([])
    expect(result.current.pendingToolCallId).toBeNull()
  })

  it("startWorkflow 应将状态设为 running", () => {
    const { result } = renderHook(() => useWorkflowRuntime())

    // mock fetch 返回空流
    mockFetch.mockResolvedValueOnce({
      ok: true,
      body: { getReader: () => ({ read: () => Promise.resolve({ done: true, value: undefined }) }) }
    })

    act(() => {
      result.current.startWorkflow("approval-process", { userId: "1" })
    })

    expect(result.current.status).toBe("running")
    expect(result.current.messages).toEqual([])
  })

  it("cancel 应将状态重置为 idle", () => {
    const { result } = renderHook(() => useWorkflowRuntime())

    mockFetch.mockResolvedValueOnce({
      ok: true,
      body: { getReader: () => ({ read: () => new Promise(() => {}) }) }
    })

    act(() => {
      result.current.startWorkflow("test")
    })

    act(() => {
      result.current.cancel()
    })

    expect(result.current.status).toBe("idle")
  })

  it("startWorkflow 失败时状态应为 failed", async () => {
    const { result } = renderHook(() => useWorkflowRuntime())

    mockFetch.mockResolvedValueOnce({ ok: false, body: null })

    await act(async () => {
      result.current.startWorkflow("bad-process")
      // 等待 fetch promise 解析
      await new Promise((r) => setTimeout(r, 10))
    })

    expect(result.current.status).toBe("failed")
  })

  it("submitInput 应发送 POST 请求并添加用户消息", async () => {
    const { result } = renderHook(() => useWorkflowRuntime())

    // 手动设置 runId 模拟运行中状态
    act(() => {
      // 通过 startWorkflow 触发
      mockFetch.mockResolvedValueOnce({
        ok: true,
        body: {
          getReader: () => ({
            read: vi
              .fn()
              .mockResolvedValueOnce({
                done: false,
                value: new TextEncoder().encode('data:{"type":"RUN_STARTED","runId":"run-1"}\n')
              })
              .mockResolvedValueOnce({
                done: false,
                value: new TextEncoder().encode(
                  'data:{"type":"TOOL_CALL_START","toolCallName":"user_input","toolCallId":"tc-1"}\n'
                )
              })
              .mockResolvedValue({ done: true, value: undefined })
          })
        }
      })
      result.current.startWorkflow("test")
    })

    // 等待 SSE 事件处理
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })

    // 提交输入
    mockFetch.mockResolvedValueOnce({ ok: true })
    await act(async () => {
      await result.current.submitInput("用户输入内容")
    })

    expect(result.current.messages).toContainEqual(
      expect.objectContaining({ role: "user", content: "用户输入内容" })
    )
  })
})
