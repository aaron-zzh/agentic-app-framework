/**
 * 数字输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"

import type { FieldProps } from "../../types"
import type { NumberField } from "../../types"

export function NumberInput({ name, value, onChange, error, disabled, field }: FieldProps<number>) {
  const numField = field as NumberField
  return (
    <div className="space-y-1">
      {field.label && <label className="text-sm font-medium">{field.label}</label>}
      <Input
        type="number"
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.valueAsNumber)}
        placeholder={field.placeholder}
        disabled={disabled}
        min={numField.min}
        max={numField.max}
        step={numField.step}
        aria-invalid={!!error}
      />
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
