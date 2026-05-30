/**
 * AI 感知便捷 Hook——提供 collectContext / recordAction / setPageContext
 * 自动过滤 aiExclude 字段的表单值
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef } from "react"

import type { FieldDef } from "@/lib/types/entity"

import type { ActionRecord, AIPageContext } from "../store/ai-awareness-store"
import { useAIAwarenessStore } from "../store/ai-awareness-store"

/**
 * 从表单值中排除标记了 aiExclude 的字段
 */
function filterExcludedFields(
  values: Record<string, unknown>,
  fields: FieldDef[]
): Record<string, unknown> {
  const excludeNames = new Set(
    fields.filter((f) => "name" in f && f.aiExclude).map((f) => (f as { name: string }).name)
  )
  const result: Record<string, unknown> = {}
  for (const [key, val] of Object.entries(values)) {
    if (!excludeNames.has(key)) {
      result[key] = val
    }
  }
  return result
}

interface UseAIAwarenessOptions {
  /** 当前实体的字段定义，用于过滤 aiExclude */
  fields?: FieldDef[]
}

/**
 * AI 感知便捷 Hook
 *
 * @example
 * const { collectContext, recordAction, setPageContext } = useAIAwareness({ fields })
 * setPageContext({ currentEntity: 'document', currentView: 'form', formValues: values })
 * recordAction({ type: 'update', entity: 'document', detail: '修改标题' })
 */
export function useAIAwareness(options: UseAIAwarenessOptions = {}) {
  const store = useAIAwarenessStore()
  // 用 ref 存储 options.fields，避免调用方每次传新数组导致 useCallback 重建
  const fieldsRef = useRef(options.fields)
  fieldsRef.current = options.fields

  const setPageContext = useCallback(
    (ctx: Partial<Omit<AIPageContext, "recentActions">>) => {
      // 如果传入 formValues 且有 fields 定义，自动过滤 aiExclude 字段
      if (ctx.formValues && fieldsRef.current) {
        ctx = { ...ctx, formValues: filterExcludedFields(ctx.formValues, fieldsRef.current) }
      }
      store.setPageContext(ctx)
    },
    [store]
  )

  const recordAction = useCallback(
    (action: Omit<ActionRecord, "timestamp">) => {
      if (!store.enabled) return
      store.recordAction(action)
    },
    [store]
  )

  const collectContext = useCallback((): AIPageContext => {
    return store.collectContext()
  }, [store])

  return {
    collectContext,
    recordAction,
    setPageContext,
    enabled: store.enabled,
    setEnabled: store.setEnabled
  }
}
