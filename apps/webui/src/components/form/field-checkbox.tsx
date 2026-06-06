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
  description?: string
  className?: string
  disabled?: boolean
}

export function FieldCheckbox({
  name,
  label,
  description,
  className,
  disabled
}: FieldCheckboxProps) {
  const { control } = useFormContext()
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("flex flex-col gap-1.5", className)}>
          <div className="flex items-center gap-2">
            <Checkbox
              id={name}
              checked={field.value ?? false}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              disabled={disabled}
              aria-invalid={!!error}
            />
            {label && <Label htmlFor={name}>{label}</Label>}
          </div>
          {description && <p className="text-muted-foreground text-xs">{description}</p>}
          {error && <p className="text-destructive text-xs">{error.message}</p>}
        </div>
      )}
    />
  )
}
