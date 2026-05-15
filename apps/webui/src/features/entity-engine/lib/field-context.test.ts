import { describe, expect, it } from "vitest"
import { buildFieldContext, resolveValue, evaluateCondition } from "./field-context"

describe("FieldContext", () => {
  const ctx = buildFieldContext(
    { status: "active", amount: 150, title: "测试" },
    { id: "u1", role: "admin", department: "tech" },
    { parent: { id: "p1" } }
  )

  describe("resolveValue", () => {
    it("解析 $record 字段", () => {
      expect(resolveValue("$record.status", ctx)).toBe("active")
      expect(resolveValue("$record.amount", ctx)).toBe(150)
    })

    it("解析 $user 字段", () => {
      expect(resolveValue("$user.role", ctx)).toBe("admin")
      expect(resolveValue("$user.id", ctx)).toBe("u1")
    })

    it("解析 $parent 字段", () => {
      expect(resolveValue("$parent.id", ctx)).toBe("p1")
    })

    it("解析 $env", () => {
      expect(resolveValue("$env.now", ctx)).toBeDefined()
    })

    it("解析字面量", () => {
      expect(resolveValue("42", ctx)).toBe(42)
      expect(resolveValue("true", ctx)).toBe(true)
      expect(resolveValue("'hello'", ctx)).toBe("hello")
    })

    it("无前缀当作 $record 字段", () => {
      expect(resolveValue("status", ctx)).toBe("active")
    })

    it("不存在的路径返回 undefined", () => {
      expect(resolveValue("$record.nonexist", ctx)).toBeUndefined()
    })
  })

  describe("evaluateCondition", () => {
    it("等于判断", () => {
      expect(evaluateCondition("$record.status == 'active'", ctx)).toBe(true)
      expect(evaluateCondition("$record.status == 'draft'", ctx)).toBe(false)
    })

    it("不等于判断", () => {
      expect(evaluateCondition("$record.status != 'draft'", ctx)).toBe(true)
    })

    it("数值比较", () => {
      expect(evaluateCondition("$record.amount > 100", ctx)).toBe(true)
      expect(evaluateCondition("$record.amount < 100", ctx)).toBe(false)
      expect(evaluateCondition("$record.amount >= 150", ctx)).toBe(true)
    })

    it("truthy 判断", () => {
      expect(evaluateCondition("$record.status", ctx)).toBe(true)
      expect(evaluateCondition("$record.nonexist", ctx)).toBe(false)
    })
  })
})
