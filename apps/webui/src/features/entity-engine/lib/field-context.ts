/**
 * FieldContext——统一表达式上下文（$record/$user/$parent/$params/$env）
 * @author AaronZZH & Kiro
 *
 * 安全求值：纯路径解析，无 eval
 */

/** 表达式上下文 */
export interface FieldContext {
  $record: Record<string, unknown>
  $user: Record<string, unknown>
  $parent?: Record<string, unknown>
  $params?: Record<string, unknown>
  $env?: Record<string, unknown>
}

/** 构建表达式上下文 */
export function buildFieldContext(
  record: Record<string, unknown>,
  user: Record<string, unknown> = {},
  options?: { parent?: Record<string, unknown>; params?: Record<string, unknown> }
): FieldContext {
  return {
    $record: record,
    $user: user,
    $parent: options?.parent,
    $params: options?.params,
    $env: { now: new Date().toISOString() }
  }
}

/**
 * 解析表达式路径，返回值
 * 支持：`$record.status`、`$user.role`、`$parent.id`、字面量
 */
export function resolveValue(expr: string, ctx: FieldContext): unknown {
  const trimmed = expr.trim()

  // 字面量：数字
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed)
  // 字面量：布尔
  if (trimmed === "true") return true
  if (trimmed === "false") return false
  // 字面量：字符串（引号包裹）
  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1)
  }

  // 路径解析：$record.field.nested
  if (trimmed.startsWith("$")) {
    const parts = trimmed.split(".")
    const root = parts[0] as keyof FieldContext
    let value: unknown = ctx[root]
    for (let i = 1; i < parts.length; i++) {
      if (value == null || typeof value !== "object") return undefined
      value = (value as Record<string, unknown>)[parts[i]]
    }
    return value
  }

  // 无前缀：当作 $record 的字段名
  return ctx.$record[trimmed]
}

/**
 * 求值简单条件表达式
 * 格式：`$record.status == 'active'` / `$record.amount > 100`
 */
export function evaluateCondition(expr: string, ctx: FieldContext): boolean {
  // 支持的操作符
  const operators = ["===", "!==", "==", "!=", ">=", "<=", ">", "<"]
  for (const op of operators) {
    const idx = expr.indexOf(op)
    if (idx === -1) continue
    const left = resolveValue(expr.slice(0, idx), ctx)
    const right = resolveValue(expr.slice(idx + op.length), ctx)
    switch (op) {
      case "===":
      case "==":
        return left === right
      case "!==":
      case "!=":
        return left !== right
      case ">":
        return Number(left) > Number(right)
      case "<":
        return Number(left) < Number(right)
      case ">=":
        return Number(left) >= Number(right)
      case "<=":
        return Number(left) <= Number(right)
    }
  }

  // 无操作符：truthy 判断
  const val = resolveValue(expr, ctx)
  return !!val
}
