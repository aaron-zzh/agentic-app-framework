/**
 * withExpectedVersion 单元测试
 */

import { describe, expect, it } from "vitest"
import { withExpectedVersion } from "./expected-version"

describe("withExpectedVersion", () => {
  it("详情版本应覆盖表单提交中的 expectedVersion", () => {
    expect(withExpectedVersion({ version: 7 }, { name: "新名称", expectedVersion: 1 })).toEqual({
      name: "新名称",
      expectedVersion: 7
    })
  })

  it("详情版本非法时应拒绝构造更新载荷", () => {
    expect(withExpectedVersion({ version: "7" }, { name: "新名称" })).toBeUndefined()
    expect(withExpectedVersion({ version: -1 }, { name: "新名称" })).toBeUndefined()
  })
})
