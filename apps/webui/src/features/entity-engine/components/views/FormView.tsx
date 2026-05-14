/**
 * 表单视图——基于 react-hook-form + Zod 实现配置驱动表单
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <FormView entity={documentEntity} data={record} onSubmit={handleSave} />
 * ```
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { getFieldComponent } from "../../lib/component-registry"
import type { DataFieldDef, EntityDef, FieldDef, LayoutField } from "../../types"
import { buildZodSchema } from "../../lib/build-zod-schema"

interface FormViewProps {
  entity: EntityDef
  data?: Record<string, unknown>
  loading?: boolean
  onSubmit?: (values: Record<string, unknown>) => void
}

/** 表单视图 */
export function FormView({ entity, data, loading, onSubmit }: FormViewProps) {
  const { fields, formView } = entity
  const schema = buildZodSchema(fields)

  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues: data ?? {}
  })

  const handleSubmit = form.handleSubmit((values) => {
    onSubmit?.(values)
  })

  if (loading) {
    return <FormSkeleton fields={fields.length} />
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 p-4">
      {formView?.layout
        ? renderLayout(formView.layout, fields, form)
        : renderLinear(fields, form)}
      <div className="flex justify-end pt-4">
        <button
          type="submit"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          保存
        </button>
      </div>
    </form>
  )
}

/** 线性渲染所有字段 */
function renderLinear(fields: FieldDef[], form: ReturnType<typeof useForm>) {
  return fields
    .filter((f) => f.type !== "group" && f.type !== "tabs" && f.type !== "row")
    .map((field) => {
      const dataField = field as DataFieldDef
      return <FieldRenderer key={dataField.name} field={dataField} form={form} />
    })
}

/** 按 layout 配置渲染（tabs/group/row） */
function renderLayout(layout: LayoutField[], fields: FieldDef[], form: ReturnType<typeof useForm>) {
  return layout.map((item, i) => {
    switch (item.type) {
      case "group":
        return (
          // biome-ignore lint/suspicious/noArrayIndexKey: layout 静态配置
          <fieldset key={i} className="space-y-3 rounded-md border p-4">
            <legend className="px-2 text-sm font-medium">{item.label}</legend>
            {item.fields.map((f) => {
              if (f.type === "group" || f.type === "tabs" || f.type === "row") return null
              const df = f as DataFieldDef
              return <FieldRenderer key={df.name} field={df} form={form} />
            })}
          </fieldset>
        )
      case "tabs":
        return (
          // biome-ignore lint/suspicious/noArrayIndexKey: layout 静态配置
          <div key={i} className="space-y-3">
            <div className="flex gap-2 border-b">
              {item.tabs.map((tab) => (
                <span key={tab.label} className="border-b-2 border-primary px-3 py-1.5 text-sm font-medium">
                  {tab.label}
                </span>
              ))}
            </div>
            {/* 渲染第一个 tab 的字段（后续可加 tab 切换状态） */}
            {item.tabs[0]?.fields.map((f) => {
              if (f.type === "group" || f.type === "tabs" || f.type === "row") return null
              const df = f as DataFieldDef
              return <FieldRenderer key={df.name} field={df} form={form} />
            })}
          </div>
        )
      case "row":
        return (
          // biome-ignore lint/suspicious/noArrayIndexKey: layout 静态配置
          <div key={i} className="flex gap-4">
            {item.fields.map((f) => {
              if (f.type === "group" || f.type === "tabs" || f.type === "row") return null
              const df = f as DataFieldDef
              return (
                <div key={df.name} style={{ width: (f as { width?: string }).width ?? "auto" }} className="flex-1">
                  <FieldRenderer field={df} form={form} />
                </div>
              )
            })}
          </div>
        )
      default:
        return null
    }
  })
}

/** 单个字段渲染器 */
function FieldRenderer({ field, form }: { field: DataFieldDef; form: ReturnType<typeof useForm> }) {
  const Component = getFieldComponent(field.type)
  const { watch, setValue, formState: { errors } } = form
  const value = watch(field.name)
  const error = errors[field.name]?.message as string | undefined

  if (!Component) {
    return (
      <div className="text-xs text-muted-foreground">
        未注册的字段类型：{field.type}（{field.name}）
      </div>
    )
  }

  return (
    <Component
      name={field.name}
      value={value ?? ""}
      onChange={(v: unknown) => setValue(field.name, v, { shouldValidate: true })}
      error={error}
      disabled={field.readOnly}
      field={field}
    />
  )
}

/** 表单骨架屏 */
function FormSkeleton({ fields }: { fields: number }) {
  return (
    <div className="space-y-4 p-4">
      {Array.from({ length: Math.min(fields, 6) }).map((_, i) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: 骨架屏静态列表
        <div key={i} className="space-y-1">
          <div className="h-4 w-20 animate-pulse rounded bg-muted" />
          <div className="h-9 animate-pulse rounded bg-muted" />
        </div>
      ))}
    </div>
  )
}
