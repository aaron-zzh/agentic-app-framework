/**
 * backend-client 聚合范围写入预拦截单元测试。
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"
import {
  backendApi,
  setBackendOrgContext
} from "@/lib/api/rest/backend-client"
import {
  installMockBackendClient,
  mockBackendRequest,
  mockBackendResponse,
  resetMockBackendClient
} from "@/test/mock-backend-client"

function ok(data: unknown = null): void {
  mockBackendResponse({ code: 0, data })
}

describe("backend-client 聚合范围保护", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    installMockBackendClient()
    resetMockBackendClient()
  })

  it.each([
    ["all", null],
    ["org-1", "all"]
  ])("聚合范围 %s/%s 在发网前拒绝 mutation", async (orgId, workspaceId) => {
    setBackendOrgContext(orgId, workspaceId)

    await expect(backendApi.put("/system/todos/1", { title: "修改" })).rejects.toMatchObject({
      code: 403,
      message: "聚合范围仅支持读取"
    })
    expect(mockBackendRequest).not.toHaveBeenCalled()
  })

  it("聚合范围允许 GET 和精确登记的纯查询 POST", async () => {
    setBackendOrgContext("org-1", "all")
    ok([])
    await backendApi.get("/system/todos")
    ok({ value: 1 })
    await backendApi.post("/system/dashboards/widgets/widget-1/data", {})

    expect(mockBackendRequest).toHaveBeenCalledTimes(2)
  })

  it("具体范围允许 mutation", async () => {
    setBackendOrgContext("org-1", "workspace-1")
    ok({ id: "1" })

    await backendApi.put("/system/todos/1", { title: "修改" })

    expect(mockBackendRequest).toHaveBeenCalledOnce()
  })
})