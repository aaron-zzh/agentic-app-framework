import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"
import type { ReactNode } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { backendApi } from "@/lib/api/rest/backend-client"
import { useFlowDeploy, useFlowList, useFlowSave } from "./use-flow-query"

vi.mock("@/lib/api/rest/backend-client", () => ({
  backendApi: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

const FLOW_WIRE = {
  id: 8,
  name: "测试工作流",
  mode: "CHAT",
  definition: JSON.stringify({ nodes: [], edges: [] }),
  status: "DRAFT",
  agentCallable: false,
  requireConfirm: true,
  createTime: "2026-07-30T10:00:00",
  updateTime: "2026-07-30T10:00:00"
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe("use-flow-query", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("应解析 PageResult 和服务端 JSON definition", async () => {
    vi.mocked(backendApi.get).mockResolvedValue({ list: [FLOW_WIRE], total: 1 })

    const { result } = renderHook(() => useFlowList(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0]).toMatchObject({
      id: "8",
      definition: { nodes: [], edges: [] },
      createTime: FLOW_WIRE.createTime
    })
  })

  it("保存时应序列化 definition", async () => {
    vi.mocked(backendApi.put).mockResolvedValue(FLOW_WIRE)
    const { result } = renderHook(() => useFlowSave(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({
        id: "8",
        name: "测试工作流",
        mode: "CHAT",
        definition: { nodes: [], edges: [] }
      })
    })

    expect(backendApi.put).toHaveBeenCalledWith(
      "/ai/workflows/8",
      expect.objectContaining({ definition: JSON.stringify({ nodes: [], edges: [] }) })
    )
  })

  it("部署时不应发送前端 BPMN XML", async () => {
    vi.mocked(backendApi.post).mockResolvedValue({ ...FLOW_WIRE, status: "PUBLISHED" })
    const { result } = renderHook(() => useFlowDeploy(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync("8")
    })

    expect(backendApi.post).toHaveBeenCalledWith("/ai/workflows/8/deploy")
  })
})
