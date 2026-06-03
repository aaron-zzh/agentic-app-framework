/**
 * useEntityRecord / useEntityMutation / useEntityDelete 单元测试
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"
import { createElement, type ReactNode } from "react"
import { beforeEach, describe, expect, it } from "vitest"
import {
  useEntityDelete,
  useEntityMutation,
  useEntityRecord
} from "@/lib/queries/use-entity-mutations"
import type { EntityDef } from "@/lib/types/entity"
import {
  installMockBackendClient,
  mockBackendRequest,
  mockBackendResponse,
  resetMockBackendClient
} from "@/test/mock-backend-client"

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

const entity = {
  slug: "task",
  apiPath: "/task",
  fields: [],
  listView: { columns: [] }
} as unknown as EntityDef

describe("useEntityRecord", () => {
  beforeEach(() => {
    installMockBackendClient()
    resetMockBackendClient()
  })

  it("根据 id 查询单条记录", async () => {
    mockBackendResponse({ code: 0, data: { id: "1", title: "任务一" } })

    const { result } = renderHook(() => useEntityRecord(entity, "1"), {
      wrapper: createWrapper()
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ id: "1", title: "任务一" })
  })

  it("id 为 undefined 时不发请求", () => {
    const { result } = renderHook(() => useEntityRecord(entity, undefined), {
      wrapper: createWrapper()
    })
    expect(result.current.fetchStatus).toBe("idle")
  })
})

describe("useEntityMutation", () => {
  beforeEach(() => {
    installMockBackendClient()
    resetMockBackendClient()
  })

  it("创建记录（无 id）", async () => {
    mockBackendResponse({ code: 0, data: { id: "new-1", title: "新任务" } })

    const { result } = renderHook(() => useEntityMutation(entity), {
      wrapper: createWrapper()
    })

    await act(async () => {
      result.current.mutate({ title: "新任务" })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({ method: "post", url: "/task" })
    )
  })

  it("更新记录（有 id）", async () => {
    mockBackendResponse({ code: 0, data: { id: "1", title: "已更新" } })

    const { result } = renderHook(() => useEntityMutation(entity, "1"), {
      wrapper: createWrapper()
    })

    await act(async () => {
      result.current.mutate({ title: "已更新" })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({ method: "put", url: "/task/1" })
    )
  })
})

describe("useEntityDelete", () => {
  beforeEach(() => {
    installMockBackendClient()
    resetMockBackendClient()
  })

  it("批量删除", async () => {
    mockBackendResponse({ code: 0, data: null })

    const { result } = renderHook(() => useEntityDelete(entity), {
      wrapper: createWrapper()
    })

    await act(async () => {
      result.current.mutate(["1", "2", "3"])
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        data: JSON.stringify({ ids: ["1", "2", "3"] }),
        method: "delete",
        url: "/task"
      })
    )
  })
})
