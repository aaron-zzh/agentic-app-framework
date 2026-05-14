/**
 * 日期输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"

import type { DateField, FieldProps } from "../../types"

export function DateInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  const dateField = field as DateField
  return (
    <div className="space-y-1">
      {field.label && (
        <label htmlFor={name} className="font-medium text-sm">
          {field.label}
        </label>
      )}
      <Input
        type={dateField.includeTime ? "datetime-local" : "date"}
        id={name}
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        aria-invalid={!!error}
      />
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
