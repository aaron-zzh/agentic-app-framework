/**
 * 字段只读展示渲染器——视图级只读态使用，复用列表 Cell 组件体系
 * @author AaronZZH & Kiro
 *
 * 与 FieldRenderer（可编辑）并列：FieldRenderer 渲染表单控件，
 * FieldViewer 渲染纯展示内容（getCellComponent 注册的 Cell 组件），
 * 保持与列表视图一致的视觉呈现（徽标、格式化日期、关联记录等）。
 */

import { getCellComponent } from "../../lib/component-registry"
import type { DataFieldDef } from "../../types"

/** 单个字段只读展示 */
export function FieldViewer({
  field,
  value,
  record,
  labelLayout = "top"
}: {
  field: DataFieldDef
  value: unknown
  record: Record<string, unknown>
  labelLayout?: "top" | "left"
}) {
  const Cell = getCellComponent(field.type)

  const content = Cell ? (
    <Cell value={value} record={record} field={field} />
  ) : (
    <span className="text-muted-foreground text-sm">{value == null ? "—" : String(value)}</span>
  )

  if (labelLayout === "left") {
    return (
      <div className="grid grid-cols-[120px_1fr] items-start gap-2">
        {field.label && (
          <span className="pt-0.5 text-right text-muted-foreground text-sm">{field.label}</span>
        )}
        <div className="text-sm">{content}</div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1">
      {field.label && <span className="text-muted-foreground text-xs">{field.label}</span>}
      <div className="text-sm">{content}</div>
    </div>
  )
}
