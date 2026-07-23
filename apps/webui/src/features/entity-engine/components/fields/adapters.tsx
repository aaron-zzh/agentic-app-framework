/**
 * 适配器组件——将 components/form/ 组件包装为 FieldProps 接口
 * @author AaronZZH & Kiro
 */

"use client"

import {
  FieldCascader,
  FieldMoney,
  FieldQuantity,
  FieldSignature,
  FieldUpload,
  RelationshipPicker,
  RichTextEditor,
  Subtable
} from "@/components/form"
import {
  type EntityRecordReference,
  EntityRecordReferencePicker
} from "@/components/form/entity-record-reference-picker"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import type { RelationOption } from "@/lib/hooks/use-relationship-picker"
import { entityRegistry } from "@/lib/modules/entity-registry"
import type {
  CascaderField,
  FieldProps,
  MoneyField,
  QuantityField,
  RecordReferenceField,
  RelationshipField,
  SignatureField,
  SubtableField,
  UploadField
} from "../../types"

type RelationshipValue = {
  id: string | number
  label?: string
  imageUrl?: string
}

function isRelationshipValue(value: unknown): value is RelationshipValue {
  return (
    typeof value === "object" &&
    value !== null &&
    "id" in value &&
    (typeof value.id === "string" || typeof value.id === "number")
  )
}

/** 关联字段适配器 */
export function RelationshipInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  const rel = field as RelationshipField
  const target = entityRegistry.requireResource(rel.relationTo)
  const targetDefinition = entityRegistry.getByResource(rel.relationTo)
  const relationValues = Array.isArray(value) ? value : value == null || value === "" ? [] : [value]
  const selectedOptions: RelationOption[] = relationValues.flatMap((item) => {
    if (!isRelationshipValue(item)) return []
    return [
      {
        id: String(item.id),
        label: item.label ?? String(item.id),
        ...(item.imageUrl ? { imageUrl: item.imageUrl } : {})
      }
    ]
  })
  const selectedIds = relationValues
    .flatMap((item) =>
      isRelationshipValue(item) ? [String(item.id)] : typeof item === "string" ? [item] : []
    )
    .filter((id) => id.trim().length > 0)
  const selectedValue = rel.hasMany ? selectedIds : (selectedIds[0] ?? "")

  return (
    <div className="flex flex-col gap-1.5">
      {field.label ? <p className="font-medium text-sm">{field.label}</p> : null}
      <RelationshipPicker
        name={name}
        value={selectedValue}
        onChange={onChange as (v: string | string[]) => void}
        error={error}
        disabled={disabled}
        field={field}
        multiple={rel.hasMany}
        placeholder={targetDefinition ? `搜索${targetDefinition.label}…` : undefined}
        displayField={rel.displayField}
        searchEndpoint={`${target.apiPath}/_options`}
        selectedOptions={selectedOptions}
      />
    </div>
  )
}

function toEntityRecordReference(value: unknown): EntityRecordReference | undefined {
  if (typeof value !== "object" || value === null || !("resource" in value) || !("id" in value)) {
    return undefined
  }
  const reference = value as Record<string, unknown>
  if (
    typeof reference.resource !== "string" ||
    (typeof reference.id !== "string" && typeof reference.id !== "number")
  ) {
    return undefined
  }
  const entity = entityRegistry.getAll().find((item) => item.resource === reference.resource)
  const id = String(reference.id)
  return {
    resource: reference.resource,
    entitySlug: entity?.slug ?? reference.resource,
    id,
    label: typeof reference.label === "string" ? reference.label : `#${id}`
  }
}

/** 跨实体记录引用字段适配器。 */
export function RecordReferenceInput({ value, onChange, error, disabled, field }: FieldProps) {
  const referenceField = field as RecordReferenceField
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <p className="font-medium text-sm">{field.label}</p>}
      <EntityRecordReferencePicker
        value={toEntityRecordReference(value)}
        onChange={onChange as (value: EntityRecordReference | undefined) => void}
        allowedEntitySlugs={referenceField.allowedEntitySlugs}
        excludeEntitySlugs={referenceField.excludeEntitySlugs}
        disabled={disabled}
        placeholder={field.placeholder}
      />
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

/** 富文本适配器 */
export function RichTextInput({ name, value, onChange, disabled, field }: FieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
      <RichTextEditor
        value={value as string}
        onChange={onChange as (v: string) => void}
        disabled={disabled}
      />
    </div>
  )
}

/** 文件上传适配器 */
export function UploadInput({
  name,
  value: _value,
  onChange: _onChange,
  disabled,
  field
}: FieldProps) {
  const uploadField = field as UploadField
  return (
    <FieldUpload
      name={name}
      label={field.label}
      accept={uploadField.accept}
      maxSize={uploadField.maxSize ? uploadField.maxSize / (1024 * 1024) : undefined}
      multiple={uploadField.multiple}
      disabled={disabled}
    />
  )
}

/** 开关适配器 */
export function SwitchInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <Switch
          id={name}
          checked={(value as boolean) ?? false}
          onCheckedChange={onChange as (v: boolean) => void}
          disabled={disabled}
        />
        {field.label && <Label htmlFor={name}>{field.label}</Label>}
      </div>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

/** 金额适配器 */
export function MoneyInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  const moneyField = field as MoneyField
  return (
    <FieldMoney
      name={name}
      label={field.label}
      value={value as { value: number; currency: string } | undefined}
      onChange={onChange as (v: { value: number; currency: string }) => void}
      currencies={moneyField.currencies}
      defaultCurrency={moneyField.defaultCurrency}
      disabled={disabled}
      error={error}
    />
  )
}

/** 数量适配器 */
export function QuantityInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  const qtyField = field as QuantityField
  return (
    <FieldQuantity
      name={name}
      label={field.label}
      value={value as { value: number; unit: string } | undefined}
      onChange={onChange as (v: { value: number; unit: string }) => void}
      units={qtyField.units}
      defaultUnit={qtyField.defaultUnit}
      disabled={disabled}
      error={error}
    />
  )
}

/** 签名适配器 */
export function SignatureInput({ name, value, onChange, disabled, field }: FieldProps) {
  const sigField = field as SignatureField
  return (
    <FieldSignature
      name={name}
      label={field.label}
      value={value as string | undefined}
      onChange={onChange as (v: string) => void}
      disabled={disabled}
      width={sigField.width}
      height={sigField.height}
    />
  )
}

/** 级联选择适配器 */
export function CascaderInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  const cascField = field as CascaderField
  return (
    <FieldCascader
      name={name}
      label={field.label}
      levels={cascField.levels.map((level) => {
        const target = entityRegistry.requireResource(level.relationTo)
        return { ...level, apiPath: `${target.apiPath}/_options` }
      })}
      value={value as string[] | undefined}
      onChange={onChange as (v: string[]) => void}
      disabled={disabled}
      error={error}
    />
  )
}

/** 子表适配器 */
// biome-ignore lint/correctness/noUnusedFunctionParameters: onChange 在 JSX 中使用
export function SubtableInput({ name, value, onChange, field, disabled }: FieldProps) {
  const subField = field as SubtableField
  // 从 columns 构造简化的 DataFieldDef 列表
  const childFields = subField.columns.map((col) => ({
    type: "text" as const,
    name: col,
    label: col
  }))
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label>{field.label}</Label>}
      <Subtable
        fields={childFields}
        value={(value as Record<string, unknown>[]) ?? []}
        onChange={onChange as (v: Record<string, unknown>[]) => void}
        disabled={disabled}
        summaryFields={subField.summary?.map((s) => s.field)}
      />
    </div>
  )
}
