/**
 * use-batch-operation.ts 单元测试
 */

import { act, renderHook, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useBatchOperation } from "./use-batch-operation"

// mock fetch
const mockFetch = vi.fn()
vi.stubGlobal("fetch", mockFetch)

// mock sonner toast
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() }
}))

const mockEntity = { apiPath: "/api/users" } as any

describe("useBatchOperation", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("初始状态应为 idle", () => {
    const { result } = renderHook(() => useBatchOperation(mockEntity))

    expect(result.current.progress.status).toBe("idle")
    expect(result.current.progress.current).toBe(0)
  })

  it("同步模式（≤100条）应直接完成", async () => {
    mockFetch.mockResolvedValueOnce({
      json: () => Promise.resolve({ data: { success: 5, failed: 0 } })
    })

    const onSuccess = vi.fn()
    const { result } = renderHook(() => useBatchOperation(mockEntity, { onSuccess }))

    await act(async () => {
      await result.current.execute("delete", ["1", "2", "3", "4", "5"])
    })

    expect(result.current.progress.status).toBe("completed")
    expect(onSuccess).toHaveBeenCalledWith({ success: 5, failed: 0 })
  })

  it("请求失败时状态应为 failed", async () => {
    mockFetch.mockRejectedValueOnce(new Error("网络错误"))

    const onError = vi.fn()
    const { result } = renderHook(() => useBatchOperation(mockEntity, { onError }))

    await act(async () => {
      await result.current.execute("delete", ["1"])
    })

    expect(result.current.progress.status).toBe("failed")
    expect(onError).toHaveBeenCalledWith("请求失败")
  })

  it("reset 应重置状态", async () => {
    mockFetch.mockResolvedValueOnce({
      json: () => Promise.resolve({ data: { success: 1, failed: 0 } })
    })

    const { result } = renderHook(() => useBatchOperation(mockEntity))

    await act(async () => {
      await result.current.execute("delete", ["1"])
    })

    act(() => {
      result.current.reset()
    })

    expect(result.current.progress.status).toBe("idle")
  })

  it("cancel 应设置状态为 cancelled", () => {
    const { result } = renderHook(() => useBatchOperation(mockEntity))

    act(() => {
      result.current.cancel()
    })

    expect(result.current.progress.status).toBe("cancelled")
  })
})
