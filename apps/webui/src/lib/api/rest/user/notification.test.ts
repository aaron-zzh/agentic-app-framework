/**
 * 通知 API facade 单元测试——验证 DELETE body 映射
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const backendApiMock = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock("../backend-client", () => ({ backendApi: backendApiMock }))

import { notificationApi } from "./notification"

describe("notificationApi", () => {
  beforeEach(() => vi.clearAllMocks())

  it("批量删除应通过 Axios delete config.data 传递 ids", async () => {
    await notificationApi.remove([1, 2])
    expect(backendApiMock.delete).toHaveBeenCalledWith("/notifications", { data: { ids: [1, 2] } })
  })
})
