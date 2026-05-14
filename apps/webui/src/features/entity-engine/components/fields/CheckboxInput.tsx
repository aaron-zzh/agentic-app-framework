/**
 * 复选框字段组件
 * @author AaronZZH & Kiro
 */

import type { FieldProps } from "../../types"

export function CheckboxInput({ name, value, onChange, disabled, field }: FieldProps<boolean>) {
  return (
    <label className="flex items-center gap-2">
      <input
        type="checkbox"
        name={name}
        checked={value ?? false}
        onChange={(e) => onChange(e.target.checked)}
        disabled={disabled}
        className="h-4 w-4 rounded border-input"
      />
      {field.label && <span className="text-sm">{field.label}</span>}
    </label>
  )
}
