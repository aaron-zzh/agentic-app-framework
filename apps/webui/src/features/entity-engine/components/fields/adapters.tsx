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
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import type {
  CascaderField,
  FieldProps,
  MoneyField,
  QuantityField,
  RelationshipField,
  SignatureField,
  SubtableField,
  UploadField
} from "../../types"

/** 关联字段适配器 */
export function RelationshipInput({ name, value, onChange, error, disabled, field }: FieldProps) {
  const rel = field as RelationshipField
  return (
    <RelationshipPicker
      name={name}
      value={value as string | string[]}
      onChange={onChange as (v: string | string[]) => void}
      error={error}
      disabled={disabled}
      field={field}
      multiple={rel.hasMany}
      displayField={rel.displayField}
      searchEndpoint={`/api/${rel.relationTo}`}
    />
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
      levels={cascField.levels.map((l) => ({ ...l, apiPath: l.apiPath ?? `/api/${l.relationTo}` }))}
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
