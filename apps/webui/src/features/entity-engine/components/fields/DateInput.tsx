/**
 * 日期输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

import type { DateField, FieldProps } from "../../types"

export function DateInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  const dateField = field as DateField
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
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
