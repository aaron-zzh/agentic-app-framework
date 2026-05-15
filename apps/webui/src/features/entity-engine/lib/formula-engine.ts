/**
 * 公式字段引擎——实时计算表达式（算术/IF/聚合/日期/文本）
 * @author AaronZZH & Kiro
 *
 * 安全求值：仅支持白名单函数，无 eval
 */

import { resolveValue, type FieldContext } from "./field-context"

/** 支持的函数 */
const functions: Record<string, (...args: number[]) => number> = {
  SUM: (...args) => args.reduce((a, b) => a + b, 0),
  AVG: (...args) => args.length ? args.reduce((a, b) => a + b, 0) / args.length : 0,
  MIN: (...args) => Math.min(...args),
  MAX: (...args) => Math.max(...args),
  ABS: (a) => Math.abs(a),
  ROUND: (a, d = 0) => Number(a.toFixed(d)),
  IF: (cond, t, f) => cond ? t : f,
}

/**
 * 计算公式表达式
 * 支持：算术（+ - * /）、字段引用（$record.price）、函数（SUM/IF/ROUND）
 */
export function evaluateFormula(formula: string, ctx: FieldContext): unknown {
  try {
    return evalExpr(formula.trim(), ctx)
  } catch {
    return undefined
  }
}

/** 递归求值 */
function evalExpr(expr: string, ctx: FieldContext): number {
  // 函数调用：FN(arg1, arg2, ...)
  const fnMatch = expr.match(/^(\w+)\((.+)\)$/)
  if (fnMatch) {
    const fnName = fnMatch[1].toUpperCase()
    const fn = functions[fnName]
    if (!fn) return 0
    const args = splitArgs(fnMatch[2]).map((a) => evalExpr(a.trim(), ctx))
    return fn(...args)
  }

  // 加减（最低优先级）
  const addIdx = findOperator(expr, ["+", "-"])
  if (addIdx > 0) {
    const left = evalExpr(expr.slice(0, addIdx).trim(), ctx)
    const op = expr[addIdx]
    const right = evalExpr(expr.slice(addIdx + 1).trim(), ctx)
    return op === "+" ? left + right : left - right
  }

  // 乘除
  const mulIdx = findOperator(expr, ["*", "/"])
  if (mulIdx > 0) {
    const left = evalExpr(expr.slice(0, mulIdx).trim(), ctx)
    const op = expr[mulIdx]
    const right = evalExpr(expr.slice(mulIdx + 1).trim(), ctx)
    return op === "*" ? left * right : right !== 0 ? left / right : 0
  }

  // 括号
  if (expr.startsWith("(") && expr.endsWith(")")) {
    return evalExpr(expr.slice(1, -1).trim(), ctx)
  }

  // 字段引用或字面量
  const val = resolveValue(expr, ctx)
  return Number(val) || 0
}

/** 在顶层（非括号内）查找操作符 */
function findOperator(expr: string, ops: string[]): number {
  let depth = 0
  for (let i = expr.length - 1; i >= 0; i--) {
    if (expr[i] === ")") depth++
    else if (expr[i] === "(") depth--
    else if (depth === 0 && ops.includes(expr[i])) return i
  }
  return -1
}

/** 按逗号分割参数（尊重括号嵌套） */
function splitArgs(str: string): string[] {
  const args: string[] = []
  let depth = 0
  let start = 0
  for (let i = 0; i < str.length; i++) {
    if (str[i] === "(") depth++
    else if (str[i] === ")") depth--
    else if (str[i] === "," && depth === 0) {
      args.push(str.slice(start, i))
      start = i + 1
    }
  }
  args.push(str.slice(start))
  return args
}
