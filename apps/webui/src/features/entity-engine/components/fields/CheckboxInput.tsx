/**
 * 复选框字段组件
 * @author AaronZZH & Kiro
 */

import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"

import type { FieldProps } from "../../types"

export function CheckboxInput({ name, value, onChange, disabled, field }: FieldProps<boolean>) {
  return (
    <div className="flex items-center gap-2">
      <Checkbox
        id={name}
        checked={value ?? false}
        onCheckedChange={(checked) => onChange(checked === true)}
        disabled={disabled}
      />
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
    </div>
  )
}
