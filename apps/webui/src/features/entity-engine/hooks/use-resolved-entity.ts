/**
 * useResolvedEntity——字典驱动 select 物化 Hook
 * @author AaronZZH & Kiro
 *
 * 扫描 entity.fields 中带 dictType 的 select 字段，从字典批量拉取数据并物化填充 options，
 * 返回一份新的 EntityDef；下游组件（QuickFilterBar/SearchBar/FilterChips 等）无需改动，
 * 仍按原有方式读取 field.options 静态数组。
 *
 * 用法：
 * ```tsx
 * const resolvedEntity = useResolvedEntity(entity)
 * <ListView entity={resolvedEntity} ... />
 * ```
 *
 * 注意：仅处理顶层字段（与项目现有 entity.fields.filter(...) 惯例一致），
 * 不递归展开 GroupField/TabsField/RowField 内的嵌套字段。
 */

"use client"

import { useMemo } from "react"

import { useDictMap } from "@/lib/hooks/use-dict"
import type { EntityDef, FieldDef, SelectField } from "../types"

function isDictDrivenSelect(field: FieldDef): field is SelectField & { dictType: string } {
  return field.type === "select" && !!field.dictType
}

/** 物化字典驱动的 select 字段，返回替换后的 EntityDef */
export function useResolvedEntity(entity: EntityDef): EntityDef {
  const { data: dictMap } = useDictMap()

  return useMemo(() => {
    if (!dictMap) return entity

    const hasDictField = entity.fields.some(isDictDrivenSelect)
    if (!hasDictField) return entity

    const fields = entity.fields.map((field) => {
      if (!isDictDrivenSelect(field)) return field
      const dictData = dictMap[field.dictType] ?? []
      return {
        ...field,
        options: dictData.map((d) => ({
          label: d.label,
          value: d.value,
          color: d.colorType
        }))
      }
    })

    return { ...entity, fields }
  }, [entity, dictMap])
}
