import { describe, expect, it } from "vitest"
import { buildFieldContext } from "./field-context"
import { evaluateFormula } from "./formula-engine"

describe("formulaEngine", () => {
  const ctx = buildFieldContext({ price: 100, quantity: 3, discount: 10, tax: 0.08 })

  it("简单算术", () => {
    expect(evaluateFormula("$record.price * $record.quantity", ctx)).toBe(300)
  })

  it("加减乘除混合", () => {
    expect(evaluateFormula("$record.price * $record.quantity - $record.discount", ctx)).toBe(290)
  })

  it("括号优先级", () => {
    expect(evaluateFormula("($record.price - $record.discount) * $record.quantity", ctx)).toBe(270)
  })

  it("SUM 函数", () => {
    expect(evaluateFormula("SUM($record.price, $record.quantity, $record.discount)", ctx)).toBe(113)
  })

  it("ROUND 函数", () => {
    expect(evaluateFormula("ROUND($record.price * $record.tax, 2)", ctx)).toBe(8)
  })

  it("IF 函数", () => {
    expect(evaluateFormula("IF($record.quantity, $record.price, 0)", ctx)).toBe(100)
  })

  it("字面量", () => {
    expect(evaluateFormula("100 * 2", ctx)).toBe(200)
  })

  it("不存在的字段返回 0", () => {
    expect(evaluateFormula("$record.nonexist * 5", ctx)).toBe(0)
  })
})
