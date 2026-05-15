import { describe, expect, it } from "vitest"
import { validateRules, type ValidationRule } from "./validation-rules"

describe("validateRules", () => {
  const rules: ValidationRule[] = [
    {
      name: "endAfterStart",
      condition: "$record.endDate > $record.startDate",
      message: "结束日期必须晚于开始日期",
      level: "error",
      fields: ["startDate", "endDate"],
    },
    {
      name: "amountWarning",
      condition: "$record.amount <= 10000",
      message: "金额超过 10000，请确认",
      level: "warning",
      fields: ["amount"],
    },
  ]

  it("全部通过", () => {
    const result = validateRules(rules, { startDate: 1, endDate: 2, amount: 500 })
    expect(result.valid).toBe(true)
    expect(result.errors).toHaveLength(0)
    expect(result.warnings).toHaveLength(0)
  })

  it("error 级别阻止提交", () => {
    const result = validateRules(rules, { startDate: 5, endDate: 3, amount: 500 })
    expect(result.valid).toBe(false)
    expect(result.errors[0].message).toBe("结束日期必须晚于开始日期")
    expect(result.errors[0].fields).toEqual(["startDate", "endDate"])
  })

  it("warning 级别不阻止提交", () => {
    const result = validateRules(rules, { startDate: 1, endDate: 2, amount: 20000 })
    expect(result.valid).toBe(true)
    expect(result.warnings[0].message).toBe("金额超过 10000，请确认")
  })
})
