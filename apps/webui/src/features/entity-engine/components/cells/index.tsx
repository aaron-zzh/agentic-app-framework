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
