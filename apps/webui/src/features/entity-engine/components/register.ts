/**
 * 默认字段组件注册——将内置组件注册到组件注册表
 * @author AaronZZH & Kiro
 *
 * 用法：在应用启动时调用一次
 * ```ts
 * import { registerDefaultComponents } from "@/features/entity-engine/components/register"
 * registerDefaultComponents()
 * ```
 */

import { registerCellType, registerFieldType } from "../lib/component-registry"

import { BadgeCell, CheckCell, DateCell, RelationCell, TextCell } from "./cells"
import {
  CheckboxInput,
  DateInput,
  NumberInput,
  SelectInput,
  TextareaInput,
  TextInput
} from "./fields"

export function registerDefaultComponents(): void {
  // 表单字段组件
  registerFieldType("text", TextInput)
  registerFieldType("email", TextInput)
  registerFieldType("textarea", TextareaInput)
  registerFieldType("number", NumberInput)
  registerFieldType("checkbox", CheckboxInput)
  registerFieldType("select", SelectInput)
  registerFieldType("date", DateInput)

  // 列表单元格组件
  registerCellType("text", TextCell)
  registerCellType("email", TextCell)
  registerCellType("textarea", TextCell)
  registerCellType("number", TextCell)
  registerCellType("date", DateCell)
  registerCellType("select", BadgeCell)
  registerCellType("checkbox", CheckCell)
  registerCellType("relationship", RelationCell)
}
