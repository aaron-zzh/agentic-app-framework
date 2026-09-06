/**
 * EntityAccess capability 单一事实源测试。
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import {
  AIGC_AUTHORITY_CODES,
  type EntityAccess,
  hasEntityCapability,
  hasEntityOperation
} from "./permission"

const access: EntityAccess = {
  read: true,
  create: false,
  update: true,
  delete: false,
  capabilities: [
    AIGC_AUTHORITY_CODES.PROJECT_ACTION,
    AIGC_AUTHORITY_CODES.OBJECT_VERSION_ADOPT,
    AIGC_AUTHORITY_CODES.WORK_PUBLISH,
    AIGC_AUTHORITY_CODES.TIMELINE_READ
  ],
  fieldAccess: {
    review: { visible: true, editable: true }
  }
}

describe("EntityAccess authority gates", () => {
  it("CRUD 只读取后端正式布尔字段", () => {
    expect(hasEntityOperation(access, "read")).toBe(true)
    expect(hasEntityOperation(access, "create")).toBe(false)
    expect(hasEntityOperation(access, "update")).toBe(true)
    expect(hasEntityOperation(access, "delete")).toBe(false)
  })

  it("自定义动作只读取完整 authority code capability", () => {
    expect(hasEntityCapability(access, AIGC_AUTHORITY_CODES.PROJECT_ACTION)).toBe(true)
    expect(hasEntityCapability(access, AIGC_AUTHORITY_CODES.OBJECT_VERSION_ADOPT)).toBe(true)
    expect(hasEntityCapability(access, AIGC_AUTHORITY_CODES.WORK_PUBLISH)).toBe(true)
    expect(hasEntityCapability(access, AIGC_AUTHORITY_CODES.TIMELINE_READ)).toBe(true)
    expect(hasEntityCapability(access, AIGC_AUTHORITY_CODES.PROJECT_REVIEW)).toBe(false)
  })

  it("缺少 access 时拒绝全部操作", () => {
    expect(hasEntityOperation(undefined, "update")).toBe(false)
    expect(hasEntityCapability(undefined, AIGC_AUTHORITY_CODES.PROJECT_ACTION)).toBe(false)
  })
})
