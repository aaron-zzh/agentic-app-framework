/**
 * Field.Checkbox——RHF 复选框控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils/cn"

export interface FieldCheckboxProps {
  name: string
  label?: string
  className?: string
  disabled?: boolean
}

export function FieldCheckbox({ name, label, className, disabled }: FieldCheckboxProps) {
  const { control } = useFormContext()
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <div className={cn("flex items-center gap-2", className)}>
          <Checkbox
            id={name}
            checked={field.value ?? false}
            onCheckedChange={(checked) => field.onChange(checked === true)}
            disabled={disabled}
          />
          {label && <Label htmlFor={name}>{label}</Label>}
        </div>
      )}
    />
  )
}
