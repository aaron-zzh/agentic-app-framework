/**
 * 多行文本输入字段组件
 * @author AaronZZH & Kiro
 */

import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"

import type { FieldProps } from "../../types"

export function TextareaInput({
  name,
  value,
  onChange,
  error,
  disabled,
  field
}: FieldProps<string>) {
  const rows = field.type === "textarea" && "rows" in field ? (field.rows ?? 3) : 3
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
      <Textarea
        id={name}
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        placeholder={field.placeholder}
        disabled={disabled}
        rows={rows}
        aria-invalid={!!error}
      />
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
