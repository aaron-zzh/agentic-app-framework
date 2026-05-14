/**
 * 多行文本输入字段组件
 * @author AaronZZH & Kiro
 */

import type { FieldProps } from "../../types"

export function TextareaInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  return (
    <div className="space-y-1">
      {field.label && <label className="text-sm font-medium">{field.label}</label>}
      <textarea
        name={name}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        placeholder={field.placeholder}
        disabled={disabled}
        rows={field.type === "textarea" && "rows" in field ? field.rows ?? 3 : 3}
        className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        aria-invalid={!!error}
      />
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
