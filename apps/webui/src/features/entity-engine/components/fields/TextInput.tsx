/**
 * 文本输入字段组件
 * @author AaronZZH & Kiro
 */

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

import type { FieldProps } from "../../types"

export function TextInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
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
