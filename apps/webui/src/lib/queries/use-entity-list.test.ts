/**
 * useEntityList Hook 单元测试——验证缓存隔离和参数变化
 * @author AaronZZH & Kiro
 */

import { renderHook, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { type ReactNode, createElement } from "react"
import { describe, expect, it, vi, beforeEach } from "vitest"

import type { EntityDef } from "@/features/entity-engine/types"
import { useEntityList } from "@/lib/queries/use-entity-list"

// mock fetch
const mockFetch = vi.fn()
global.fetch = mockFetch

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

const mockEntity: Pick<EntityDef, "slug" | "apiPath" | "fields" | "listView"> = {
  slug: "task",
  apiPath: "/api/task",
  fields: [],
  listView: { columns: [] },
}

function mockApiResponse(list: Record<string, unknown>[], total = list.length) {
  mockFetch.mockResolvedValueOnce({
    ok: true,
    json: async () => ({ code: 0, data: { list, total, page: 1, pageSize: 20 } }),
  })
}

describe("useEntityList", () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  it("获取列表数据", async () => {
    mockApiResponse([{ id: "1", title: "任务一" }])

    const { result } = renderHook(
      () => useEntityList(mockEntity as EntityDef),
      { wrapper: createWrapper() }
    )

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data).toEqual([{ id: "1", title: "任务一" }])
    expect(result.current.pagination.total).toBe(1)
  })

  it("不同参数产生不同缓存", async () => {
    mockApiResponse([{ id: "1" }])
    mockApiResponse([{ id: "2" }])

    const { result, rerender } = renderHook(
      ({ page }) => useEntityList(mockEntity as EntityDef, { page }),
      { wrapper: createWrapper(), initialProps: { page: 1 } }
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.data).toEqual([{ id: "1" }])

    rerender({ page: 2 })

    await waitFor(() => expect(result.current.data).toEqual([{ id: "2" }]))
  })

  it("不同实体缓存隔离", async () => {
    mockApiResponse([{ id: "task-1" }])

    const otherEntity = { ...mockEntity, slug: "document", apiPath: "/api/document" }
    mockApiResponse([{ id: "doc-1" }])

    const wrapper = createWrapper()

    const { result: r1 } = renderHook(
      () => useEntityList(mockEntity as EntityDef),
      { wrapper }
    )
    const { result: r2 } = renderHook(
      () => useEntityList(otherEntity as EntityDef),
      { wrapper }
    )

    await waitFor(() => expect(r1.current.isLoading).toBe(false))
    await waitFor(() => expect(r2.current.isLoading).toBe(false))

    expect(r1.current.data).toEqual([{ id: "task-1" }])
    expect(r2.current.data).toEqual([{ id: "doc-1" }])
  })
})
