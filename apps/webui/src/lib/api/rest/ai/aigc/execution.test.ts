/**
 * Execution run-tree API 单元测试。
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock("../../backend-client", () => ({ backendApi: { get, post: vi.fn() } }))

import { aigcExecutionApi } from "./execution"

describe("aigcExecutionApi run tree", () => {
  beforeEach(() => get.mockReset())

  it("root 分页只发一个列表请求，不自动逐 root 拉 tree", async () => {
    get.mockResolvedValueOnce({
      list: [{ id: 11 }, { id: 12 }],
      total: 3,
      pageNo: 1,
      pageSize: 2
    })

    const result = await aigcExecutionApi.runs({
      projectId: 7,
      pageNo: 1,
      pageSize: 2,
      rootOnly: true
    })

    expect(get).toHaveBeenCalledOnce()
    expect(get).toHaveBeenCalledWith("/aigc/execution-runs", {
      params: { pageNo: 1, pageSize: 2, projectId: 7, rootOnly: true }
    })
    expect(result.list).toHaveLength(2)
  })

  it("仅在调用单个 root tree 时请求对应树端点", async () => {
    get.mockResolvedValueOnce({ root: { id: 11 }, descendants: [{ id: 21 }] })

    const result = await aigcExecutionApi.tree(11)

    expect(get).toHaveBeenCalledOnce()
    expect(get).toHaveBeenCalledWith("/aigc/execution-runs/11/tree")
    expect(result.descendants).toHaveLength(1)
  })
})
