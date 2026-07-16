/**
 * 仪表盘 API facade 单元测试——验证 backendApi 请求构造
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

import { type DashboardWidgetVO, dashboardApi, type WidgetConfig } from "./dashboard"

const widget: DashboardWidgetVO = {
  id: "w1",
  type: "counter",
  title: "计数",
  position: { x: 0, y: 0, w: 4, h: 2 },
  config: { type: "counter", entity: "user", aggregation: "count" }
}
const widgetConfig: WidgetConfig = { type: "counter", entity: "order", aggregation: "count" }

describe("dashboardApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("预设和仪表盘读取端点应使用 GET", async () => {
    await dashboardApi.listPresets()
    await dashboardApi.listMetrics()
    await dashboardApi.list()
    await dashboardApi.get("d1")
    await dashboardApi.getDefault()
    expect(backendApiMock.get).toHaveBeenNthCalledWith(1, "/system/dashboards/presets")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(2, "/system/dashboards/metrics")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(3, "/system/dashboards")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(4, "/system/dashboards/d1")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(5, "/system/dashboards/default")
  })

  it("预设写操作应保留 verb、路径和负载", async () => {
    await dashboardApi.createPreset({ name: "默认" })
    await dashboardApi.updatePreset("p1", { description: "说明" })
    await dashboardApi.deletePreset("p1")
    expect(backendApiMock.post).toHaveBeenCalledWith("/system/dashboards/presets", { name: "默认" })
    expect(backendApiMock.put).toHaveBeenCalledWith("/system/dashboards/presets/p1", {
      description: "说明"
    })
    expect(backendApiMock.delete).toHaveBeenCalledWith("/system/dashboards/presets/p1")
  })

  it("仪表盘写操作应保留 verb、路径和负载", async () => {
    await dashboardApi.saveLayout("d1", [widget])
    await dashboardApi.create({ name: "新仪表盘", shared: true })
    await dashboardApi.rename("d1", "新名称")
    await dashboardApi.delete("d1")
    expect(backendApiMock.put).toHaveBeenNthCalledWith(1, "/system/dashboards/d1/layout", {
      layout: [widget]
    })
    expect(backendApiMock.post).toHaveBeenCalledWith("/system/dashboards", {
      name: "新仪表盘",
      shared: true
    })
    expect(backendApiMock.put).toHaveBeenNthCalledWith(2, "/system/dashboards/d1", {
      name: "新名称"
    })
    expect(backendApiMock.delete).toHaveBeenCalledWith("/system/dashboards/d1")
  })

  it("Widget 数据应使用 POST 并传递完整配置", async () => {
    await dashboardApi.getWidgetData("w1", widgetConfig)
    expect(backendApiMock.post).toHaveBeenCalledWith(
      "/system/dashboards/widgets/w1/data",
      widgetConfig
    )
  })
})
