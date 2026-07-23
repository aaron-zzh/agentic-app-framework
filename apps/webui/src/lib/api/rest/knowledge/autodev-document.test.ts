/**
 * AutoDev 文档 API facade 单元测试——验证原始内容更新契约
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

import { autodevDocApi } from "./autodev-document"

describe("autodevDocApi", () => {
  beforeEach(() => vi.clearAllMocks())

  it("更新应传递原始 content 字符串而非 JSON 对象", async () => {
    await autodevDocApi.update(42, "# 开发文档")
    expect(backendApiMock.put).toHaveBeenCalledWith("/autodev/docs/42", "# 开发文档")
  })
})
