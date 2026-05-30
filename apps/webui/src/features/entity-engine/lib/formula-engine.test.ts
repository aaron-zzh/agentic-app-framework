/**
 * formula-engine 单元测试
 * @author Kiro
 */

import { describe, expect, it } from "vitest"
import { evaluateFormula } from "./formula-engine"
import type { FieldContext } from "./field-context"

const ctx: FieldContext = {
  $record: { price: 10, quantity: 3, discount: 2 },
  $user: { id: "u1", role: "admin", permissions: [] },
  $params: {},
  $env: { locale: "zh", isMobile: false }
}

describe("evaluateFormula", () => {
  it("左结合减法：1-2-3 = -4", () => {
    expect(evaluateFormula("1-2-3", ctx)).toBe(-4)
  })

  it("乘法优先于减法：2*3-4 = 2", () => {
    expect(evaluateFormula("2*3-4", ctx)).toBe(2)
  })

  it("IF 函数：IF(1, 10, 20) = 10", () => {
    expect(evaluateFormula("IF(1, 10, 20)", ctx)).toBe(10)
  })

  it("IF 函数 falsy：IF(0, 10, 20) = 20", () => {
    expect(evaluateFormula("IF(0, 10, 20)", ctx)).toBe(20)
  })

  it("字段引用：$record.price * $record.quantity", () => {
    expect(evaluateFormula("$record.price * $record.quantity", ctx)).toBe(30)
  })
})
