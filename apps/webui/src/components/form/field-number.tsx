/**
 * Field.Number——RHF 数字输入控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils/cn"

export interface FieldNumberProps {
  name: string
  label?: string
  description?: string
  placeholder?: string
  min?: number
  max?: number
  step?: number
  className?: string
  disabled?: boolean
}

export function FieldNumber({
  name,
  label,
  description,
  placeholder,
  min,
  max,
  step,
  className,
  disabled
}: FieldNumberProps) {
  const { control } = useFormContext()
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <Input
              id={name}
              type="number"
              placeholder={placeholder}
              disabled={disabled}
              min={min}
              max={max}
              step={step}
              aria-invalid={!!error}
              {...field}
              value={field.value ?? ""}
              onChange={(e) => field.onChange(e.target.valueAsNumber)}
            />
            {description && <p className="text-muted-foreground text-xs">{description}</p>}
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}
