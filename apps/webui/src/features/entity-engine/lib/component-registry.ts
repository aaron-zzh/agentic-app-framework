/**
 * 组件注册表——字段类型 → UI 组件映射 + 扩展 API
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```ts
 * // 注册字段组件
 * registerFieldType("text", TextInput)
 * registerCellType("text", TextCell)
 *
 * // 视图引擎中获取组件
 * const Input = getFieldComponent(field.type) // → TextInput
 * const Cell = getCellComponent(field.type)   // → TextCell
 *
 * // 注册批量操作
 * registerBatchAction({ key: "archive", label: "归档", handler: async (ids) => { ... } })
 *
 * // 获取某实体可用的批量操作
 * const actions = getBatchActions("document")
 * ```
 */

import type { ComponentType } from "react"

import type { CellProps, FieldProps } from "../types"

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 注册表需要接受任意泛型的组件
type AnyFieldComponent = ComponentType<FieldProps<any>>
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnyCellComponent = ComponentType<CellProps<any>>

/** 表单字段组件映射：字段 type → React 组件 */
const fieldComponents = new Map<string, AnyFieldComponent>()

/** 列表单元格组件映射：字段 type → React 组件 */
const cellComponents = new Map<string, AnyCellComponent>()

/** 视图类型组件映射：view type → React 组件 */
const viewComponents = new Map<string, ComponentType>()

/** 批量操作注册表 */
export interface BatchActionDef {
  key: string
  label: string
  icon?: string
  handler: (ids: string[]) => Promise<void>
  /** 仅特定实体可用（空则全部可用） */
  visibleFor?: string[]
}

const batchActions = new Map<string, BatchActionDef>()

/** 注册表单字段组件 */
export function registerFieldType(type: string, component: AnyFieldComponent): void {
  fieldComponents.set(type, component)
}

/** 注册列表单元格组件 */
export function registerCellType(type: string, component: AnyCellComponent): void {
  cellComponents.set(type, component)
}

/** 注册视图类型组件 */
export function registerViewType(type: string, component: ComponentType): void {
  viewComponents.set(type, component)
}

/** 注册批量操作 */
export function registerBatchAction(action: BatchActionDef): void {
  batchActions.set(action.key, action)
}

/** 获取表单字段组件 */
export function getFieldComponent(type: string): AnyFieldComponent | undefined {
  return fieldComponents.get(type)
}

/** 获取列表单元格组件 */
export function getCellComponent(type: string): AnyCellComponent | undefined {
  return cellComponents.get(type)
}

/** 获取视图类型组件 */
export function getViewComponent(type: string): ComponentType | undefined {
  return viewComponents.get(type)
}

/** 获取批量操作（按实体过滤） */
export function getBatchActions(entitySlug?: string): BatchActionDef[] {
  return Array.from(batchActions.values()).filter(
    (a) => !a.visibleFor?.length || (entitySlug && a.visibleFor.includes(entitySlug))
  )
}

/** 清空注册表（测试用） */
export function clearComponentRegistry(): void {
  fieldComponents.clear()
  cellComponents.clear()
  viewComponents.clear()
  batchActions.clear()
}
