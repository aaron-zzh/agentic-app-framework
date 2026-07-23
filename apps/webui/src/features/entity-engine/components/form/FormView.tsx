/**
 * 表单视图——基于 react-hook-form + Zod 实现配置驱动表单
 * @author AaronZZH & Kiro
 *
 * 用法：
  ```tsx
 * <FormView entity={documentEntity} data={record} onSubmit={handleSave} />
 * // 视图级只读展示态（复用 Cell 组件体系，不渲染表单控件和保存按钮）
 * <FormView entity={documentEntity} data={record} readOnly />
 * ```
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import type { ReactNode } from "react"
import { useEffect } from "react"
import { FormProvider, useForm, useFormContext } from "react-hook-form"

import { FieldErrorBoundary } from "@/components/common/FieldErrorBoundary"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { useAuthStore } from "@/lib/store/auth-store"
import { useConditionalFields } from "../../hooks/use-conditional-fields"
import { buildZodSchema } from "../../lib/build-zod-schema"
import { getFieldComponent } from "../../lib/component-registry"
import type { DataFieldDef, EntityDef, FieldDef, LayoutField } from "../../types"
import { registerDefaultComponents } from "../register"
import { FieldViewer } from "./FieldViewer"

// 确保默认组件已注册（幂等，多次调用安全）
registerDefaultComponents()

interface FormViewProps {
  entity: EntityDef
  data?: Record<string, unknown>
  loading?: boolean
  onSubmit?: (values: Record<string, unknown>) => void
  /** 供外部 submit 按钮关联的原生表单 ID。 */
  externalFormId?: string
  /** 是否渲染表单底部保存动作；默认渲染。 */
  showSubmitAction?: boolean
  /** 表单模式决定 displayModes 配置字段是否渲染；默认编辑模式。 */
  mode?: "create" | "edit"
  /** 视图级只读展示态——为真时渲染 Cell 展示组件，不渲染表单控件，无保存按钮 */
  readOnly?: boolean
}

/** 审计元信息字段名（固定渲染到表单末尾只读区） */
const AUDIT_FIELD_NAMES = new Set(["createTime", "updateTime", "createBy", "updateBy"])
/** 软删除字段名（不渲染） */
const HIDDEN_FIELD_NAMES = new Set(["deleted", "deleteTime"])

type FormMode = NonNullable<FormViewProps["mode"]>

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function relationWriteValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(relationWriteValue)
  if (isRecord(value) && "id" in value) return value.id
  return value
}

function isVisibleInMode(field: FieldDef, mode: FormMode): boolean {
  return !("displayModes" in field) || !field.displayModes || field.displayModes.includes(mode)
}

function isVisibleForRoles(field: FieldDef, userRoles?: string[]): boolean {
  if (!("visibleRoles" in field) || !field.visibleRoles?.length) return true
  const normalizedRoles = new Set(userRoles?.map((role) => role.toLowerCase()))
  return field.visibleRoles.some((role) => normalizedRoles.has(role.toLowerCase()))
}

function filterFieldsForMode(fields: FieldDef[], mode: FormMode, userRoles?: string[]): FieldDef[] {
  const filteredFields: FieldDef[] = []
  for (const field of fields) {
    switch (field.type) {
      case "group": {
        const groupFields = filterFieldsForMode(field.fields, mode, userRoles)
        if (groupFields.length > 0) filteredFields.push({ ...field, fields: groupFields })
        break
      }
      case "tabs": {
        const tabs = field.tabs
          .map((tab) => ({ ...tab, fields: filterFieldsForMode(tab.fields, mode, userRoles) }))
          .filter((tab) => tab.fields.length > 0)
        if (tabs.length > 0) filteredFields.push({ ...field, tabs })
        break
      }
      case "row": {
        const rowFields = filterFieldsForMode(field.fields, mode, userRoles)
        if (rowFields.length > 0) {
          filteredFields.push({ ...field, fields: rowFields as typeof field.fields })
        }
        break
      }
      default:
        if (isVisibleInMode(field, mode) && isVisibleForRoles(field, userRoles)) {
          filteredFields.push(field)
        }
    }
  }
  return filteredFields
}

function serializeRecordReference(
  field: DataFieldDef,
  value: unknown
): Record<string, unknown> | null {
  if (field.type !== "recordReference") return null
  if (!isRecord(value)) return { [field.writeKey]: null }
  const resource = value.resource
  const id = value.id
  if (typeof resource !== "string" || (typeof id !== "string" && typeof id !== "number")) {
    return { [field.writeKey]: null }
  }
  if (field.idValueType === "number") {
    const numericId = Number(id)
    if (!Number.isFinite(numericId)) return null
    return { [field.writeKey]: { resource, id: numericId } }
  }
  return { [field.writeKey]: { resource, id: String(id) } }
}

/** 仅提交可写字段；关联展示对象按 writeKey 转为后端 DTO 所需 ID。 */
function serializeEntityValues(fields: FieldDef[], values: Record<string, unknown>) {
  const payload: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.type === "group" || field.type === "tabs" || field.type === "row" || field.readOnly) {
      continue
    }
    const value = values[field.name]
    const recordReference = serializeRecordReference(field, value)
    if (recordReference) {
      Object.assign(payload, recordReference)
      continue
    }
    if (value === undefined) continue
    const key = field.type === "relationship" ? (field.writeKey ?? field.name) : field.name
    payload[key] = field.type === "relationship" ? relationWriteValue(value) : value
  }
  return payload
}

/** 表单视图 */
export function FormView({
  entity,
  data,
  loading,
  onSubmit,
  externalFormId,
  showSubmitAction = !externalFormId,
  mode = "edit",
  readOnly
}: FormViewProps) {
  const userRoles = useAuthStore((state) => state.user?.roles)
  const { fields, formView } = entity
  const labelLayout = formView?.labelLayout ?? "top"
  const modeFields = filterFieldsForMode(fields, mode, userRoles)
  const layout = formView?.layout
    ? filterFieldsForMode(formView.layout, mode, userRoles)
    : undefined

  // 过滤掉软删除字段和 hidden 字段，审计字段单独处理
  const visibleFields = modeFields.filter((f) => {
    if (!("name" in f)) return true
    const df = f as DataFieldDef
    if (HIDDEN_FIELD_NAMES.has(df.name)) return false
    if (df.hidden) return false
    if (AUDIT_FIELD_NAMES.has(df.name)) return false
    return true
  })

  const auditFields = fields.filter(
    (f): f is DataFieldDef => "name" in f && AUDIT_FIELD_NAMES.has((f as DataFieldDef).name)
  )

  const schema = buildZodSchema(visibleFields)
  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues: data ?? {}
  })

  // data 异步加载完成后重新填充表单——defaultValues 只在挂载时生效一次，
  // 详情面板打开时 data 通常晚于组件挂载到达，需显式 reset 同步最新值
  useEffect(() => {
    if (data) form.reset(data)
  }, [data, form])

  const handleSubmit = form.handleSubmit((values) => {
    onSubmit?.(serializeEntityValues(modeFields, values))
  })

  if (loading) {
    return <FormSkeleton fields={fields.length} />
  }

  return (
    <FormProvider {...form}>
      <form
        id={externalFormId}
        onSubmit={readOnly ? undefined : handleSubmit}
        className="@container min-h-[240px] space-y-4 overflow-auto @lg:p-6 @sm:p-5 p-4"
      >
        {layout
          ? renderLayout(
              layout as LayoutField[],
              visibleFields,
              entity,
              labelLayout,
              readOnly,
              data
            )
          : renderLinear(visibleFields, entity, labelLayout, readOnly, data)}

        {/* 审计信息只读区 */}
        {auditFields.length > 0 && data && <AuditInfo fields={auditFields} data={data} />}

        {/* 只读态、无更新权限或外置保存动作时不渲染表单底部保存按钮 */}
        {showSubmitAction && !readOnly && onSubmit && (
          <div className="flex justify-end pt-4">
            <button
              type="submit"
              className="rounded-md bg-primary px-4 py-2 font-medium text-primary-foreground text-sm hover:bg-primary/90"
            >
              保存
            </button>
          </div>
        )}
      </form>
    </FormProvider>
  )
}

/** 线性渲染所有字段 */
function renderLinear(
  fields: FieldDef[],
  entity: EntityDef,
  labelLayout: "top" | "left",
  readOnly?: boolean,
  record?: Record<string, unknown>
) {
  return (
    <div data-slot="form-fields" className="grid @sm:grid-cols-2 grid-cols-1 gap-4">
      <ConditionalFields
        fields={fields}
        entity={entity}
        labelLayout={labelLayout}
        readOnly={readOnly}
        record={record}
      />
    </div>
  )
}

/** 混合渲染：FieldDef[] 中可能包含布局字段（row/group）和数据字段 */
function renderMixed(
  fieldDefs: FieldDef[],
  allFields: FieldDef[],
  entity: EntityDef,
  labelLayout: "top" | "left",
  readOnly?: boolean,
  record?: Record<string, unknown>
) {
  return fieldDefs.map((f, i) => {
    if (f.type === "group" || f.type === "tabs" || f.type === "row") {
      return renderLayoutItem(f as LayoutField, i, allFields, entity, labelLayout, readOnly, record)
    }
    return (
      <ConditionalFields
        key={(f as DataFieldDef).name}
        fields={[f as DataFieldDef]}
        entity={entity}
        labelLayout={labelLayout}
        readOnly={readOnly}
        record={record}
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
  labelLayout: "top" | "left",
  readOnly?: boolean,
  record?: Record<string, unknown>
): ReactNode {
  switch (item.type) {
    case "group":
      return (
        <fieldset key={`group-${i}`} className="space-y-3 rounded-md border p-4">
          <legend className="px-2 font-medium text-sm">{item.label}</legend>
          {renderMixed(item.fields, fields, entity, labelLayout, readOnly, record)}
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
            readOnly={readOnly}
            record={record}
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
              (f): f is DataFieldDef & { width?: string } =>
                f.type !== "group" && f.type !== "tabs" && f.type !== "row"
            )
            .map((f) =>
              readOnly ? (
                <FieldViewerConnected
                  key={f.name}
                  field={f}
                  labelLayout={labelLayout}
                  record={record}
                />
              ) : (
                <FieldRenderer key={f.name} field={f} labelLayout={labelLayout} />
              )
            )}
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
  labelLayout: "top" | "left",
  readOnly?: boolean,
  record?: Record<string, unknown>
) {
  return layout.map((item, i) =>
    renderLayoutItem(item, i, fields, entity, labelLayout, readOnly, record)
  )
}

/**
 * 条件字段渲染器——在 FormProvider 上下文内调用 useConditionalFields
 * 根据 visibleWhen/readOnlyWhen/requiredWhen 控制字段显示
 */
function ConditionalFields({
  fields,
  entity: _entity,
  labelLayout,
  readOnly,
  record
}: {
  fields: FieldDef[]
  entity: EntityDef
  labelLayout: "top" | "left"
  readOnly?: boolean
  record?: Record<string, unknown>
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
        if (readOnly) {
          return (
            <FieldViewerConnected
              key={field.name}
              field={field}
              labelLayout={labelLayout}
              record={record}
            />
          )
        }
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

/** 只读态字段展示——从 FormProvider 上下文取当前值传给 FieldViewer */
function FieldViewerConnected({
  field,
  labelLayout,
  record
}: {
  field: DataFieldDef
  labelLayout: "top" | "left"
  record?: Record<string, unknown>
}) {
  const { watch } = useFormContext()
  const value = watch(field.name)
  return <FieldViewer field={field} value={value} record={record ?? {}} labelLayout={labelLayout} />
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

  const displayValue = value != null ? String(value) : ""
  const {
    ref: dragRef,
    listeners,
    attributes,
    isDragging
  } = useSemanticDraggable({
    id: `field-${field.name}`,
    item: {
      type: "field",
      title: `${field.label ?? field.name}=${displayValue}`,
      semantics: { componentName: "FormField", fieldData: { [field.name]: value } }
    },
    disabled: !value
  })

  if (!Component) {
    return (
      <div className="text-muted-foreground text-xs">
        未注册的字段类型：{field.type}（{field.name}）
      </div>
    )
  }

  const fieldEl = (
    <div className="group relative" style={{ opacity: isDragging ? 0.5 : 1 }}>
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
      {/* 拖放到对话 handle */}
      {value != null && value !== "" && (
        <span
          ref={dragRef}
          {...listeners}
          {...attributes}
          className="absolute top-0 right-0 hidden cursor-grab rounded p-0.5 text-muted-foreground text-xs opacity-60 hover:opacity-100 group-hover:inline-block"
          title="拖放到对话"
        >
          💬
        </span>
      )}
    </div>
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

/** 审计信息只读展示区 */
function AuditInfo({ fields, data }: { fields: DataFieldDef[]; data: Record<string, unknown> }) {
  const timeFields = fields.filter((f) => f.type === "date")
  const userFields = fields.filter((f) => f.type !== "date")

  const renderValue = (f: DataFieldDef) => {
    const val = data[f.name]
    if (val == null) return <span className="text-muted-foreground">—</span>

    if (f.type === "date") {
      const str = String(val)
      const display = /^\d{4}-\d{2}-\d{2}T/.test(str) ? new Date(str).toLocaleString("zh-CN") : str
      return <span>{display}</span>
    }

    if (typeof val === "object" && val !== null) {
      const o = val as Record<string, unknown>
      const name = String(
        o.label ?? o.displayName ?? o.nickname ?? o.username ?? o.name ?? o.title ?? o.id ?? "—"
      )
      const imageUrl = o.imageUrl ?? o.avatar ?? o.imgUrl ?? o.avatarUrl
      return (
        <div className="flex items-center gap-1.5">
          <Avatar size="sm">
            {imageUrl ? <AvatarImage src={String(imageUrl)} alt="" /> : null}
            <AvatarFallback>{name.slice(0, 1)}</AvatarFallback>
          </Avatar>
          <span>{name}</span>
        </div>
      )
    }

    return <span>{String(val)}</span>
  }

  return (
    <div className="rounded-md border border-dashed px-4 py-3 text-sm">
      {timeFields.length > 0 && (
        <div className="mb-2 grid grid-cols-2 gap-x-6 gap-y-2">
          {timeFields.map((f) => (
            <div key={f.name} className="flex items-center gap-2">
              <span className="w-16 shrink-0 text-right text-muted-foreground text-xs">
                {f.label ?? f.name}
              </span>
              {renderValue(f)}
            </div>
          ))}
        </div>
      )}
      {userFields.length > 0 && (
        <div className="grid grid-cols-2 gap-x-6 gap-y-2">
          {userFields.map((f) => (
            <div key={f.name} className="flex items-center gap-2">
              <span className="w-16 shrink-0 text-right text-muted-foreground text-xs">
                {f.label ?? f.name}
              </span>
              {renderValue(f)}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/** 表单骨架屏 */
function FormSkeleton({ fields }: { fields: number }) {
  return (
    <div className="space-y-4 p-4">
      {Array.from({ length: Math.min(fields, 6) }).map((_, i) => (
        <div key={i} className="space-y-1">
          <div className="h-4 w-20 animate-pulse rounded bg-muted" />
          <div className="h-9 animate-pulse rounded bg-muted" />
        </div>
      ))}
    </div>
  )
}
