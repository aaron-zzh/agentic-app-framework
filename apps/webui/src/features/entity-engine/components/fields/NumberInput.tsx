/**
 * 数字输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"
import type { FieldProps, NumberField } from "../../types"

export function NumberInput({ name, value, onChange, error, disabled, field }: FieldProps<number>) {
  const numField = field as NumberField
  return (
    <div className="space-y-1">
      {field.label && (
        <label htmlFor={name} className="font-medium text-sm">
          {field.label}
        </label>
      )}
      <Input
        type="number"
        id={name}
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
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
