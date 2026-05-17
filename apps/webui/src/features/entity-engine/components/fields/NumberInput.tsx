/**
 * 数字输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

import type { FieldProps, NumberField } from "../../types"

export function NumberInput({ name, value, onChange, error, disabled, field }: FieldProps<number>) {
  const numField = field as NumberField
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
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
