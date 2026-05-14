import { beforeEach, describe, expect, it } from "vitest"

import {
  clearComponentRegistry,
  getBatchActions,
  getCellComponent,
  getFieldComponent,
  getViewComponent,
  registerBatchAction,
  registerCellType,
  registerFieldType,
  registerViewType,
} from "./component-registry"

describe("componentRegistry", () => {
  beforeEach(() => {
    clearComponentRegistry()
  })

  it("注册并获取表单字段组件", () => {
    const MockInput = () => null
    registerFieldType("text", MockInput)
    expect(getFieldComponent("text")).toBe(MockInput)
  })

  it("注册并获取列表单元格组件", () => {
    const MockCell = () => null
    registerCellType("date", MockCell)
    expect(getCellComponent("date")).toBe(MockCell)
  })

  it("注册并获取视图类型组件", () => {
    const MockView = () => null
    registerViewType("kanban", MockView)
    expect(getViewComponent("kanban")).toBe(MockView)
  })

  it("未注册的类型返回 undefined", () => {
    expect(getFieldComponent("unknown")).toBeUndefined()
    expect(getCellComponent("unknown")).toBeUndefined()
    expect(getViewComponent("unknown")).toBeUndefined()
  })

  it("注册并获取批量操作", () => {
    registerBatchAction({
      key: "archive",
      label: "归档",
      handler: async () => {},
    })
    const actions = getBatchActions()
    expect(actions).toHaveLength(1)
    expect(actions[0].key).toBe("archive")
  })

  it("批量操作按实体过滤", () => {
    registerBatchAction({
      key: "sendEmail",
      label: "发送邮件",
      handler: async () => {},
      visibleFor: ["contact"],
    })
    expect(getBatchActions("contact")).toHaveLength(1)
    expect(getBatchActions("task")).toHaveLength(0)
  })
})
