/**
 * 文本输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"

import type { FieldProps } from "../../types"

export function TextInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  return (
    <div className="space-y-1">
      {field.label && (
        <label htmlFor={name} className="font-medium text-sm">
          {field.label}
        </label>
      )}
      <Input
        id={name}
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        placeholder={field.placeholder}
        disabled={disabled}
        aria-invalid={!!error}
      />
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
