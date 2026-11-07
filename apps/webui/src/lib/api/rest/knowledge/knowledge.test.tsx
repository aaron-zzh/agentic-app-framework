/**
 * 知识库 API 与 Query hooks 单元测试
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook } from "@testing-library/react"
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

import { knowledgeApi, useDeleteKnowledgeDocument } from "./knowledge"

const DOCUMENT: KnowledgeDocument = {
  id: 31,
  stableId: "00000000-0000-0000-0000-000000000031",
  knowledgeBaseId: 12,
  sourceDocumentId: 88,
  sourceDocumentVersionId: 89,
  uploadedBy: 7,
  sourceType: "FILE",
  sourceKey: "architecture.md",
  sourceUri: "/knowledge/architecture.md",
  activeRunId: "00000000-0000-0000-0000-000000000099",
  title: "架构说明",
  summary: null,
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

  it("检索应发送授权多库 wire contract 并默认包含公共库", async () => {
    const request = {
      query: "实体注册表",
      knowledgeBaseIds: ["00000000-0000-0000-0000-000000000012"],
      sourceFilters: { sourceTypes: ["FILE" as const] },
      topK: 8,
      threshold: 0.65,
      mode: "hybrid" as const
    }

    await knowledgeApi.search(request)

    expect(backendApiMock.post).toHaveBeenCalledWith("/knowledge-bases/search", {
      ...request,
      includePublic: true
    })
  })

  it("纯图检索应按 graph 模式发送授权多库请求", async () => {
    const request = {
      query: "实体关系",
      knowledgeBaseIds: ["00000000-0000-0000-0000-000000000012"],
      topK: 5,
      threshold: 0,
      mode: "graph" as const
    }

    await knowledgeApi.search(request)

    expect(backendApiMock.post).toHaveBeenCalledWith("/knowledge-bases/search", {
      ...request,
      includePublic: true
    })
  })
})

describe("知识文档 Query hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    backendApiMock.delete.mockResolvedValue(undefined)
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
