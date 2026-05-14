/**
 * Field.Number——数字输入
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface FieldNumberProps {
  name: string
  label?: string
  placeholder?: string
  min?: number
  max?: number
  step?: number
  className?: string
  disabled?: boolean
}

export function FieldNumber({ name, label, placeholder, min, max, step, className, disabled }: FieldNumberProps) {
  const { control } = useFormContext()

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("space-y-1", className)}>
          {label && <label className="text-sm font-medium">{label}</label>}
          <input
            type="number"
            value={field.value ?? ""}
            onChange={(e) => field.onChange(e.target.value === "" ? undefined : Number(e.target.value))}
            onBlur={field.onBlur}
            ref={field.ref}
            min={min}
            max={max}
            step={step}
            placeholder={placeholder}
            disabled={disabled}
            className={cn(
              "flex h-9 w-full rounded-md border bg-transparent px-3 py-1 text-sm shadow-xs placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
              error && "border-destructive"
            )}
          />
          {error && <p className="text-xs text-destructive">{error.message}</p>}
        </div>
      )}
    />
  )
}
