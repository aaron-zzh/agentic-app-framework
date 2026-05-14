/**
 * Field.Checkbox——复选框
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

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
      render={({ field, fieldState: { error } }) => (
        <div className={cn("flex items-center gap-2", className)}>
          <input
            type="checkbox"
            checked={field.value ?? false}
            onChange={(e) => field.onChange(e.target.checked)}
            onBlur={field.onBlur}
            ref={field.ref}
            disabled={disabled}
            className="size-4 rounded border"
          />
          {label && <label className="text-sm">{label}</label>}
          {error && <p className="text-xs text-destructive">{error.message}</p>}
        </div>
      )}
    />
  )
}
