import { beforeEach, describe, expect, it } from "vitest"
import {
  installMockBackendClient,
  mockBackendRequest,
  mockBackendResponse,
  resetMockBackendClient
} from "@/test/mock-backend-client"
import {
  buildQuery,
  deleteRecord,
  deleteRecords,
  fetchList,
  fetchQueryWindow
} from "./client"
import { crudResources } from "../endpoints"

describe("crud client", () => {
  beforeEach(() => {
    installMockBackendClient()
    resetMockBackendClient()
  })

  it("应生成稳定的查询字符串并跳过空值", () => {
    expect(
      buildQuery({
        pageNo: 1,
        pageSize: 20,
        search: "",
        enabled: true,
        tags: ["a", "b"],
        empty: undefined
      })
    ).toBe("?pageNo=1&pageSize=20&enabled=true&tags=a&tags=b")
  })

  it("应请求基础分页列表", async () => {
    mockBackendResponse({ code: 0, data: { list: [], total: 0 } })

    await fetchList(crudResources.system.menus, { pageNo: 1, pageSize: 10 })

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: "get",
        url: "/system/menus?pageNo=1&pageSize=10"
      })
    )
  })

  it("应支持通过资源注册表请求列表", async () => {
    mockBackendResponse({ code: 0, data: { list: [], total: 0 } })

    const page = await fetchList(crudResources.system.menus, { pageNo: 1 })

    expect(page.list).toEqual([])
    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: "get",
        url: "/system/menus?pageNo=1"
      })
    )
  })

  it("应请求标准查询窗口", async () => {
    mockBackendResponse({ code: 0, data: { list: [], total: 0 } })

    await fetchQueryWindow(crudResources.system.menus, { pageNo: 1, fieldSet: "list" })

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: "get",
        url: "/system/menus/_query?pageNo=1&fieldSet=list"
      })
    )
  })

  it("单条删除应请求 DELETE /{id}", async () => {
    mockBackendResponse({ code: 0, data: null })

    await deleteRecord(crudResources.system.menus, 1)

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: "delete",
        url: "/system/menus/1"
      })
    )
  })

  it("批量删除应请求 POST /_batch-delete", async () => {
    mockBackendResponse({ code: 0, data: null })

    await deleteRecords(crudResources.system.menus, [1, 2])

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        data: JSON.stringify({ ids: [1, 2] }),
        method: "post",
        url: "/system/menus/_batch-delete"
      })
    )
  })
})
