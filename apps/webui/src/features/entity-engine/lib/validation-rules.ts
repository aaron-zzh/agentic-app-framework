/**
 * 跨字段校验规则——实体级 ValidationRule 求值
 * @author AaronZZH & Kiro
 */

import { buildFieldContext, evaluateCondition } from "./field-context"

/** 校验规则定义 */
export interface ValidationRule {
  /** 规则名称 */
  name: string
  /** 条件表达式（为 true 时校验通过） */
  condition: string
  /** 错误消息 */
  message: string
  /** 级别：error 阻止提交，warning 仅提示 */
  level: "error" | "warning"
  /** 关联字段（高亮显示） */
  fields?: string[]
}

/** 校验结果 */
export interface ValidationResult {
  valid: boolean
  errors: { rule: string; message: string; fields: string[] }[]
  warnings: { rule: string; message: string; fields: string[] }[]
}

/** 执行跨字段校验 */
export function validateRules(
  rules: ValidationRule[],
  record: Record<string, unknown>,
  user: Record<string, unknown> = {}
): ValidationResult {
  const ctx = buildFieldContext(record, user)
  const errors: ValidationResult["errors"] = []
  const warnings: ValidationResult["warnings"] = []

  for (const rule of rules) {
    const passed = evaluateCondition(rule.condition, ctx)
    if (!passed) {
      const item = { rule: rule.name, message: rule.message, fields: rule.fields ?? [] }
      if (rule.level === "error") errors.push(item)
      else warnings.push(item)
    }
  }

  return { valid: errors.length === 0, errors, warnings }
}
