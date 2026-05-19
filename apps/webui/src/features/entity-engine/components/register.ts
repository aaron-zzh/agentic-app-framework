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

import {
  BadgeCell,
  CheckCell,
  CodeCell,
  DateCell,
  FormulaCell,
  JsonCell,
  MoneyCell,
  RelationCell,
  RichTextCell,
  SwitchCell,
  TextCell,
  UploadCell
} from "./cells"
import {
  CascaderInput,
  CheckboxInput,
  DateInput,
  MoneyInput,
  NumberInput,
  QuantityInput,
  RelationshipInput,
  RichTextInput,
  SelectInput,
  SignatureInput,
  SubtableInput,
  SwitchInput,
  TextareaInput,
  TextInput,
  UploadInput
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
  registerFieldType("relationship", RelationshipInput)
  registerFieldType("richText", RichTextInput)
  registerFieldType("upload", UploadInput)
  registerFieldType("switch", SwitchInput)
  registerFieldType("money", MoneyInput)
  registerFieldType("quantity", QuantityInput)
  registerFieldType("signature", SignatureInput)
  registerFieldType("cascader", CascaderInput)
  registerFieldType("subtable", SubtableInput)

  // 列表单元格组件
  registerCellType("text", TextCell)
  registerCellType("email", TextCell)
  registerCellType("textarea", TextCell)
  registerCellType("number", TextCell)
  registerCellType("date", DateCell)
  registerCellType("select", BadgeCell)
  registerCellType("checkbox", CheckCell)
  registerCellType("relationship", RelationCell)
  registerCellType("richText", RichTextCell)
  registerCellType("upload", UploadCell)
  registerCellType("json", JsonCell)
  registerCellType("code", CodeCell)
  registerCellType("switch", SwitchCell)
  registerCellType("money", MoneyCell)
  registerCellType("formula", FormulaCell)
}
