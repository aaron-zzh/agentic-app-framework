/**
 * useRelationshipPicker 单元测试——验证关系候选请求行为
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { renderHook, waitFor } from "@testing-library/react"
import { createElement, type ReactNode } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"

const backendApiMock = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock("@/lib/api/rest/backend-client", () => ({ backendApi: backendApiMock }))

import { useRelationshipPicker } from "./use-relationship-picker"

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe("useRelationshipPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it("首次加载默认请求 20 条候选记录", async () => {
    backendApiMock.get.mockResolvedValue([{ id: 8, label: "王五" }])

    const { result } = renderHook(() => useRelationshipPicker("/system/users/_options"), {
      wrapper: createWrapper()
    })

    await waitFor(() =>
      expect(backendApiMock.get).toHaveBeenCalledWith("/system/users/_options?limit=20")
    )
    await waitFor(() => expect(result.current.displayOptions).toEqual([{ id: "8", label: "王五" }]))
  })
})
