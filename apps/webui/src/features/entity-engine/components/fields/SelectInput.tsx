/**
 * 下拉选择字段组件
 * @author AaronZZH & Kiro
 */

import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

import type { FieldProps, SelectField } from "../../types"

export function SelectInput({ name, value, onChange, error, disabled, field }: FieldProps<string>) {
  const selectField = field as SelectField
  return (
    <div className="flex flex-col gap-1.5">
      {field.label && <Label htmlFor={name}>{field.label}</Label>}
      <Select
        value={value ?? ""}
        onValueChange={(v) => onChange(v ?? "")}
        disabled={disabled}
        aria-invalid={!!error}
      >
        <SelectTrigger id={name} className="w-full">
          <SelectValue placeholder={field.placeholder ?? "请选择"} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {selectField.options?.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
