/**
 * 下拉选择字段组件
 * @author AaronZZH & Kiro
 */

import { Label } from "@/components/ui/label"

import type { FieldProps, SelectField } from "../../types"

export function SelectInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  const selectField = field as SelectField
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
      <select
        id={name}
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        className="flex h-8 w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive"
        aria-invalid={!!error}
      >
        <option value="">{field.placeholder ?? "请选择"}</option>
        {selectField.options?.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
