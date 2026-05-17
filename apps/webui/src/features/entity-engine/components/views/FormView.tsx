/**
 * 表单视图——基于 react-hook-form + Zod 实现配置驱动表单
 * @author AaronZZH & Kiro
 *
 * 用法：
  ```tsx
 * <FormView entity={documentEntity} data={record} onSubmit={handleSave} />
 * ```
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import type { ReactNode } from "react"
import { FormProvider, useForm, useFormContext } from "react-hook-form"

import { FieldErrorBoundary } from "@/components/common/FieldErrorBoundary"
import { useConditionalFields } from "../../hooks/use-conditional-fields"
import { buildZodSchema } from "../../lib/build-zod-schema"
import { getFieldComponent } from "../../lib/component-registry"
import type { DataFieldDef, EntityDef, FieldDef, LayoutField } from "../../types"
import { registerDefaultComponents } from "../register"

// 确保默认组件已注册（幂等，多次调用安全）
registerDefaultComponents()

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
  const labelLayout = formView?.labelLayout ?? "top"

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
    <FormProvider {...form}>
      <form onSubmit={handleSubmit} className="space-y-4 p-4">
        {formView?.layout
          ? renderLayout(formView.layout, fields, entity, labelLayout)
          : renderLinear(fields, entity, labelLayout)}
        <div className="flex justify-end pt-4">
          <button
            type="submit"
            className="rounded-md bg-primary px-4 py-2 font-medium text-primary-foreground text-sm hover:bg-primary/90"
          >
            保存
          </button>
        </div>
      </form>
    </FormProvider>
  )
}

/** 线性渲染所有字段 */
function renderLinear(fields: FieldDef[], entity: EntityDef, labelLayout: "top" | "left") {
  return <ConditionalFields fields={fields} entity={entity} labelLayout={labelLayout} />
}

/** 混合渲染：FieldDef[] 中可能包含布局字段（row/group）和数据字段 */
function renderMixed(
  fieldDefs: FieldDef[],
  allFields: FieldDef[],
  entity: EntityDef,
  labelLayout: "top" | "left"
) {
  return fieldDefs.map((f, i) => {
    if (f.type === "group" || f.type === "tabs" || f.type === "row") {
      return renderLayoutItem(f as LayoutField, i, allFields, entity, labelLayout)
    }
    return (
      <ConditionalFields
        key={(f as DataFieldDef).name}
        fields={[f as DataFieldDef]}
        entity={entity}
        labelLayout={labelLayout}
      />
    )
  })
}

/** 渲染单个布局项 */
function renderLayoutItem(
  item: LayoutField,
  i: number,
  fields: FieldDef[],
  entity: EntityDef,
  labelLayout: "top" | "left"
): ReactNode {
  switch (item.type) {
    case "group":
      return (
        <fieldset key={`group-${i}`} className="space-y-3 rounded-md border p-4">
          <legend className="px-2 font-medium text-sm">{item.label}</legend>
          {renderMixed(item.fields, fields, entity, labelLayout)}
        </fieldset>
      )
    case "tabs":
      return (
        <div key={`tabs-${i}`} className="space-y-3">
          <div className="flex gap-2 border-b">
            {item.tabs.map((tab) => (
              <span
                key={tab.label}
                className="border-primary border-b-2 px-3 py-1.5 font-medium text-sm"
              >
                {tab.label}
              </span>
            ))}
          </div>
          <ConditionalFields
            fields={item.tabs[0]?.fields ?? []}
            entity={entity}
            labelLayout={labelLayout}
          />
        </div>
      )
    case "row":
      return (
        <div
          key={`row-${i}`}
          className="grid gap-4"
          style={{ gridTemplateColumns: `repeat(${item.fields.length}, 1fr)` }}
        >
          {item.fields
            .filter(
              (f): f is DataFieldDef => f.type !== "group" && f.type !== "tabs" && f.type !== "row"
            )
            .map((f) => (
              <FieldRenderer key={f.name} field={f} labelLayout={labelLayout} />
            ))}
        </div>
      )
    default:
      return null
  }
}

/** 按 layout 配置渲染（tabs/group/row） */
function renderLayout(
  layout: LayoutField[],
  fields: FieldDef[],
  entity: EntityDef,
  labelLayout: "top" | "left"
) {
  return layout.map((item, i) => renderLayoutItem(item, i, fields, entity, labelLayout))
}

/**
 * 条件字段渲染器——在 FormProvider 上下文内调用 useConditionalFields
 * 根据 visibleWhen/readOnlyWhen/requiredWhen 控制字段显示
 */
function ConditionalFields({
  fields,
  entity: _entity,
  labelLayout
}: {
  fields: FieldDef[]
  entity: EntityDef
  labelLayout: "top" | "left"
}) {
  const dataFields = fields.filter(
    (f): f is DataFieldDef => f.type !== "group" && f.type !== "tabs" && f.type !== "row"
  )
  const conditions = useConditionalFields(dataFields)

  return (
    <>
      {dataFields.map((field) => {
        const state = conditions[field.name]
        if (!state?.visible) return null
        return (
          <FieldRenderer
            key={field.name}
            field={field}
            readOnly={state.readOnly}
            labelLayout={labelLayout}
          />
        )
      })}
    </>
  )
}

/** 单个字段渲染器 */
function FieldRenderer({
  field,
  readOnly,
  labelLayout = "top"
}: {
  field: DataFieldDef
  readOnly?: boolean
  labelLayout?: "top" | "left"
}) {
  const Component = getFieldComponent(field.type)
  const {
    watch,
    setValue,
    formState: { errors }
  } = useFormContext()
  const value = watch(field.name)
  const error = errors[field.name]?.message as string | undefined

  if (!Component) {
    return (
      <div className="text-muted-foreground text-xs">
        未注册的字段类型：{field.type}（{field.name}）
      </div>
    )
  }

  const fieldEl = (
    <FieldErrorBoundary fieldName={field.label ?? field.name}>
      <Component
        name={field.name}
        value={value ?? ""}
        onChange={(v: unknown) => setValue(field.name, v, { shouldValidate: true })}
        error={error}
        disabled={readOnly ?? field.readOnly}
        field={field}
      />
    </FieldErrorBoundary>
  )

  if (labelLayout === "left" && field.label) {
    // 外层渲染 label，组件内部传空 label 避免重复
    const fieldWithoutLabel = { ...field, label: undefined }
    return (
      <div className="grid grid-cols-[120px_1fr] items-start gap-2">
        <label htmlFor={field.name} className="pt-2 text-right text-muted-foreground text-sm">
          {field.label}
        </label>
        <div>
          <FieldErrorBoundary fieldName={field.label ?? field.name}>
            <Component
              name={field.name}
              value={value ?? ""}
              onChange={(v: unknown) => setValue(field.name, v, { shouldValidate: true })}
              error={error}
              disabled={readOnly ?? field.readOnly}
              field={fieldWithoutLabel as DataFieldDef}
            />
          </FieldErrorBoundary>
        </div>
      </div>
    )
  }

  return fieldEl
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
