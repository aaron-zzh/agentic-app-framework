/**
 * dashboard.ts API 单元测试——验证仪表盘接口的请求构造
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { dashboardApi } from "./dashboard"

vi.mock("./client", () => ({
  request: vi.fn()
}))

import { request } from "./client"
const mockRequest = vi.mocked(request)

describe("dashboardApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("list 应请求 GET /dashboards", async () => {
    mockRequest.mockResolvedValueOnce([])

    await dashboardApi.list()

    expect(mockRequest).toHaveBeenCalledWith("/dashboards")
  })

  it("get 应请求 GET /dashboards/:id", async () => {
    mockRequest.mockResolvedValueOnce({ id: "d1", name: "test", layout: [] })

    await dashboardApi.get("d1")

    expect(mockRequest).toHaveBeenCalledWith("/dashboards/d1")
  })

  it("getDefault 应请求 GET /dashboards/default", async () => {
    mockRequest.mockResolvedValueOnce({ id: "default", name: "默认", layout: [] })

    await dashboardApi.getDefault()

    expect(mockRequest).toHaveBeenCalledWith("/dashboards/default")
  })

  it("create 应发送 POST /dashboards", async () => {
    mockRequest.mockResolvedValueOnce({ id: "new", name: "新仪表盘", layout: [] })

    await dashboardApi.create({ name: "新仪表盘", shared: true })

    expect(mockRequest).toHaveBeenCalledWith("/dashboards", {
      method: "POST",
      body: JSON.stringify({ name: "新仪表盘", shared: true })
    })
  })

  it("saveLayout 应发送 PUT /dashboards/:id/layout", async () => {
    mockRequest.mockResolvedValueOnce(undefined)
    const layout = [{ id: "w1", type: "counter" as const, title: "计数", position: { x: 0, y: 0, w: 4, h: 2 }, config: { type: "counter" as const, entity: "user", aggregation: "count" as const } }]

    await dashboardApi.saveLayout("d1", layout)

    expect(mockRequest).toHaveBeenCalledWith("/dashboards/d1/layout", {
      method: "PUT",
      body: JSON.stringify({ layout })
    })
  })

  it("getWidgetData 应发送 POST /dashboards/widgets/:id/data", async () => {
    mockRequest.mockResolvedValueOnce({ value: 42 })
    const config = { type: "counter" as const, entity: "order", aggregation: "count" as const }

    await dashboardApi.getWidgetData("w1", config)

    expect(mockRequest).toHaveBeenCalledWith("/dashboards/widgets/w1/data", {
      method: "POST",
      body: JSON.stringify(config)
    })
  })
})
