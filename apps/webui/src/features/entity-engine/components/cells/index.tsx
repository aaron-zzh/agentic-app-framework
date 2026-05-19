/**
 * 列表单元格组件集
 * @author AaronZZH & Kiro
 */

import type { CellProps, RelationshipField, SelectField } from "../../types"

/** 文本单元格 */
export function TextCell({ value }: CellProps<string>) {
  return <span className="truncate">{value ?? "—"}</span>
}

/** 日期单元格 */
export function DateCell({ value }: CellProps<string>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  const date = new Date(value)
  return <span>{date.toLocaleDateString("zh-CN")}</span>
}

/** 徽章单元格（select 字段） */
export function BadgeCell({ value, field }: CellProps<string>) {
  const selectField = field as SelectField
  const option = selectField.options?.find((o) => o.value === value)
  if (!option) return <span className="text-muted-foreground">—</span>
  return (
    <span className="inline-flex items-center rounded-full bg-secondary px-2 py-0.5 font-medium text-secondary-foreground text-xs">
      {option.label}
    </span>
  )
}

/** 复选框单元格 */
export function CheckCell({ value }: CellProps<boolean>) {
  return <span>{value ? "✓" : "—"}</span>
}

/** 富文本单元格——HTML 截断展示 */
export function RichTextCell({ value }: CellProps<string>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  // 去除 HTML 标签后截断
  const text = value.replace(/<[^>]*>/g, "")
  return <span className="truncate">{text.length > 80 ? `${text.slice(0, 80)}…` : text}</span>
}

/** 文件上传单元格——展示文件名 */
export function UploadCell({ value }: CellProps<unknown>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  if (Array.isArray(value)) {
    return <span className="truncate">{value.length} 个文件</span>
  }
  const name = typeof value === "object" && value !== null
    ? ((value as Record<string, unknown>).name as string) ?? ((value as Record<string, unknown>).filename as string)
    : String(value)
  return <span className="truncate">{name ?? "—"}</span>
}

/** JSON 单元格——预览 */
export function JsonCell({ value }: CellProps<unknown>) {
  if (value === null || value === undefined) return <span className="text-muted-foreground">—</span>
  const preview = JSON.stringify(value).slice(0, 60)
  return (
    <code className="truncate rounded bg-muted px-1 py-0.5 font-mono text-xs">
      {preview.length >= 60 ? `${preview}…` : preview}
    </code>
  )
}

/** 代码单元格——代码片段预览 */
export function CodeCell({ value }: CellProps<string>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  const preview = value.split("\n")[0]?.slice(0, 60) ?? ""
  return (
    <code className="truncate rounded bg-muted px-1 py-0.5 font-mono text-xs">
      {preview}
    </code>
  )
}

/** 开关单元格——复用 CheckCell 逻辑 */
export function SwitchCell({ value }: CellProps<boolean>) {
  return <span>{value ? "✓" : "—"}</span>
}

/** 金额单元格——格式化展示 */
export function MoneyCell({ value }: CellProps<{ value: number; currency: string } | null>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  return (
    <span>
      {value.value.toLocaleString("zh-CN", { minimumFractionDigits: 2 })} {value.currency}
    </span>
  )
}

/** 公式单元格——复用 TextCell */
export function FormulaCell({ value }: CellProps<unknown>) {
  return <span className="truncate">{value != null ? String(value) : "—"}</span>
}

/** 关联单元格——头像/颜色 + 名称，点击跳转实体详情 */
export function RelationCell({ value, field }: CellProps<unknown>) {
  if (!value) return <span className="text-muted-foreground">—</span>

  const rel = field as RelationshipField

  // 从关联对象中提取展示信息
  const obj =
    typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null
  const displayName = obj
    ? ((obj.displayName ??
        obj.nickname ??
        obj.username ??
        obj.name ??
        obj.title ??
        obj.label ??
        obj.id) as string)
    : String(value)
  const avatar = obj?.avatar ?? obj?.imgUrl ?? obj?.avatarUrl
  const color = obj?.color as string | undefined
  const id = obj?.id as string | undefined

  const inner = (
    <div className="flex items-center gap-1.5">
      {/* 头像或颜色圆点或首字母 */}
      <span
        className="flex size-5 shrink-0 items-center justify-center rounded-full font-medium text-xs"
        style={color ? { backgroundColor: color, color: "#fff" } : undefined}
      >
        {avatar ? (
          // biome-ignore lint/performance/noImgElement: 关联头像为动态 URL
          <img
            src={String(avatar)}
            alt={displayName}
            className="size-5 rounded-full object-cover"
          />
        ) : color ? null : (
          <span className="flex size-5 items-center justify-center rounded-full bg-primary/10 text-primary">
            {displayName?.slice(0, 1) ?? "?"}
          </span>
        )}
      </span>
      <span className="truncate text-sm">{displayName}</span>
    </div>
  )

  if (!id || !rel?.relationTo) return inner

  return (
    <a
      href={`/workspace/${rel.relationTo}/${id}`}
      className="hover:underline"
      onClick={(e) => e.stopPropagation()}
    >
      {inner}
    </a>
  )
}
