/**
 * useConditionalFields——条件可见性引擎
 * @author AaronZZH & Kiro
 *
 * 实时计算 visibleWhen / readOnlyWhen / requiredWhen
 *
 * TODO: useWatch() 无参数监听所有字段变化，大表单（>20 字段）可能有性能问题。
 * 优化方向：提取条件表达式中引用的字段名，只 watch 这些字段。
 */

"use client"

import { useMemo } from "react"
import { useWatch } from "react-hook-form"

import type { DataFieldDef } from "@/features/entity-engine/types"
import { buildFieldContext, evaluateCondition, type FieldContext } from "../lib/field-context"

/** 字段条件状态 */
export interface FieldConditionState {
  visible: boolean
  readOnly: boolean
  required: boolean
}

/** 条件可见性 Hook */
export function useConditionalFields(
  fields: DataFieldDef[],
  user: Record<string, unknown> = {}
): Record<string, FieldConditionState> {
  // 监听所有表单值变化
  const formValues = useWatch() as Record<string, unknown>

  return useMemo(() => {
    const ctx: FieldContext = buildFieldContext(formValues ?? {}, user)
    const result: Record<string, FieldConditionState> = {}

    for (const field of fields) {
      const visible = field.visibleWhen ? evaluateCondition(field.visibleWhen, ctx) : true
      const readOnly = field.readOnlyWhen
        ? evaluateCondition(field.readOnlyWhen, ctx)
        : !!field.readOnly
      const required = field.requiredWhen
        ? evaluateCondition(field.requiredWhen, ctx)
        : !!field.required

      result[field.name] = { visible, readOnly, required }
    }

    return result
  }, [fields, formValues, user])
}
