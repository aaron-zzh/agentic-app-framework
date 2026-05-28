/**
 * 变量解析/引用工具
 * @author AaronZZH & Kiro
 */

import type { VariableDef } from "../types"

/** 变量引用正则：${varName} */
const VAR_PATTERN = /\$\{(\w+)\}/g

/** 解析字符串中的变量引用，返回引用的变量名列表 */
export function extractVariableRefs(text: string): string[] {
  const refs: string[] = []
  let match: RegExpExecArray | null
  while ((match = VAR_PATTERN.exec(text)) !== null) {
    refs.push(match[1])
  }
  return refs
}

/** 校验变量引用是否合法（所有引用的变量都已定义） */
export function validateVariableRefs(text: string, variables: VariableDef[]): string[] {
  const refs = extractVariableRefs(text)
  const defined = new Set(variables.map((v) => v.name))
  const errors: string[] = []
  for (const ref of refs) {
    if (!defined.has(ref)) {
      errors.push(`未定义的变量: ${ref}`)
    }
  }
  return errors
}

/** 替换变量引用为实际值 */
export function resolveVariables(text: string, values: Record<string, unknown>): string {
  return text.replace(VAR_PATTERN, (_, name: string) => {
    return name in values ? String(values[name]) : `\${${name}}`
  })
}
