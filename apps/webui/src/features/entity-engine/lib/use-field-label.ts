/**
 * useFieldLabel——字段标签国际化 hook
 * @author AaronZZH & Kiro
 *
 * 解析 FieldDef 的标签：优先使用 labelKey 翻译，fallback 到 label，再 fallback 到 name。
 * 同时支持 SelectOption 的 labelKey 国际化。
 *
 * 用法：
 * ```tsx
 * const { getFieldLabel, getOptionLabel } = useFieldLabel()
 * const label = getFieldLabel(field) // 返回翻译后的标签
 * const optionLabel = getOptionLabel(option) // 返回翻译后的选项标签
 * ```
 */

"use client"

import { useTranslations } from "next-intl"

import type { FieldDef, SelectOption } from "../../types"

/** 扩展 BaseFieldDef，支持 labelKey */
interface FieldWithLabelKey {
  name: string
  label?: string
  labelKey?: string
}

/** 扩展 SelectOption，支持 labelKey */
interface OptionWithLabelKey extends SelectOption {
  labelKey?: string
}

export function useFieldLabel() {
  const t = useTranslations()

  /**
   * 获取字段的国际化标签
   * 优先级：labelKey 翻译 > label > name
   */
  function getFieldLabel(field: FieldDef): string {
    // 布局字段直接返回 label
    if (field.type === "group" || field.type === "tabs" || field.type === "row") {
      if ("label" in field) return field.label
      return ""
    }

    const f = field as FieldWithLabelKey
    if (f.labelKey) {
      try {
        return t(f.labelKey)
      } catch {
        // labelKey 翻译不存在时 fallback
      }
    }
    return f.label ?? f.name
  }

  /**
   * 获取选项的国际化标签
   * 优先级：labelKey 翻译 > label
   */
  function getOptionLabel(option: OptionWithLabelKey): string {
    if (option.labelKey) {
      try {
        return t(option.labelKey)
      } catch {
        // labelKey 翻译不存在时 fallback
      }
    }
    return option.label
  }

  return { getFieldLabel, getOptionLabel }
}
