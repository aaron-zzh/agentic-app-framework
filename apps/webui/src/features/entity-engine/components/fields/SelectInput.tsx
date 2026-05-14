/**
 * 下拉选择字段组件
 * @author AaronZZH & Kiro
 */

import type { FieldProps, SelectField } from "../../types"

export function SelectInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  const selectField = field as SelectField
  return (
    <div className="space-y-1">
      {field.label && <label className="text-sm font-medium">{field.label}</label>}
      <select
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        aria-invalid={!!error}
      >
        <option value="">{field.placeholder ?? "请选择"}</option>
        {selectField.options?.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
