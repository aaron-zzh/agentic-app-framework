/**
 * 知识库 API 与 Query hooks 单元测试
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"
import type { PropsWithChildren } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import type { KnowledgeDocument } from "@/lib/types/knowledge"

const backendApiMock = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock("../backend-client", () => ({ backendApi: backendApiMock }))

import {
  getKnowledgeDocumentsRefetchInterval,
  knowledgeApi,
  useDeleteKnowledgeDocument,
  useKnowledgeDocuments
} from "./knowledge"

const DOCUMENT: KnowledgeDocument = {
  id: 31,
  knowledgeBaseId: 12,
  title: "架构说明",
  filePath: "/knowledge/architecture.md",
  fileType: "md",
  fileSize: 1024,
  contentHash: "sha256:test",
  status: 2,
  errorMessage: null,
  chunkCount: 4,
  createTime: "2026-07-31T10:00:00",
  updateTime: "2026-07-31T10:01:00"
}

describe("knowledgeApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    backendApiMock.delete.mockResolvedValue(undefined)
    backendApiMock.get.mockResolvedValue(DOCUMENT)
    backendApiMock.post.mockResolvedValue(DOCUMENT)
  })

  it("应调用统一的文档详情、删除和重试端点", async () => {
    await knowledgeApi.document(12, 31)
    await knowledgeApi.deleteDocument(12, 31)
    await knowledgeApi.retryDocument(12, 31)

    expect(backendApiMock.get).toHaveBeenCalledWith("/knowledge-bases/12/documents/31")
    expect(backendApiMock.delete).toHaveBeenCalledWith("/knowledge-bases/12/documents/31")
    expect(backendApiMock.post).toHaveBeenCalledWith("/knowledge-bases/12/documents/31/retry")
  })

  it("检索应原样发送统一 wire contract", async () => {
    const request = {
      query: "实体注册表",
      topK: 8,
      threshold: 0.65,
      mode: "hybrid" as const
    }

    await knowledgeApi.search(12, request)

    expect(backendApiMock.post).toHaveBeenCalledWith("/knowledge-bases/12/search", request)
  })
})

describe("知识文档 Query hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    backendApiMock.delete.mockResolvedValue(undefined)
  })

  it("仅存在待处理或处理中记录时返回两秒轮询间隔", () => {
    expect(getKnowledgeDocumentsRefetchInterval(undefined)).toBe(false)
    expect(
      getKnowledgeDocumentsRefetchInterval({ list: [{ ...DOCUMENT, status: 0 }], total: 1 })
    ).toBe(2000)
    expect(
      getKnowledgeDocumentsRefetchInterval({ list: [{ ...DOCUMENT, status: 1 }], total: 1 })
    ).toBe(2000)
    expect(
      getKnowledgeDocumentsRefetchInterval({
        list: [DOCUMENT, { ...DOCUMENT, id: 32, status: 3 }],
        total: 2
      })
    ).toBe(false)
  })

  it("文档从处理中进入全部终态时应各刷新一次 stats 和 graph", async () => {
    const activePage = { list: [{ ...DOCUMENT, status: 1 as const }], total: 1 }
    const terminalPage = { list: [DOCUMENT], total: 1 }
    backendApiMock.get.mockResolvedValueOnce(activePage).mockResolvedValueOnce(terminalPage)

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
    })
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries").mockResolvedValue()
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useKnowledgeDocuments("12"), { wrapper })

    await waitFor(() => expect(result.current.data).toEqual(activePage))
    await act(async () => {
      await result.current.refetch()
    })
    await waitFor(() => expect(result.current.data).toEqual(terminalPage))
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledTimes(2))

    expect(invalidateSpy).toHaveBeenNthCalledWith(1, {
      queryKey: ["knowledge-bases", "12", "stats"]
    })
    expect(invalidateSpy).toHaveBeenNthCalledWith(2, {
      queryKey: ["knowledge-bases", "12", "graph"]
    })
  })

  it("删除文档后应只失效当前知识库的 documents、stats 和 graph", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
    })
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries").mockResolvedValue()
    const removeSpy = vi.spyOn(queryClient, "removeQueries")
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useDeleteKnowledgeDocument("12"), { wrapper })

    await act(async () => {
      await result.current.mutateAsync(31)
    })

    expect(backendApiMock.delete).toHaveBeenCalledWith("/knowledge-bases/12/documents/31")
    expect(removeSpy).toHaveBeenCalledWith({
      queryKey: ["knowledge-bases", "12", "documents", "detail", 31]
    })
    expect(invalidateSpy).toHaveBeenCalledTimes(3)
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["knowledge-bases", "12", "documents", "list"]
    })
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["knowledge-bases", "12", "stats"]
    })
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["knowledge-bases", "12", "graph"]
    })
  })
})
