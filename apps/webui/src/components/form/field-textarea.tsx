/**
 * Field.Textarea——多行文本输入
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface FieldTextareaProps {
  name: string
  label?: string
  placeholder?: string
  rows?: number
  className?: string
  disabled?: boolean
}

export function FieldTextarea({ name, label, placeholder, rows = 3, className, disabled }: FieldTextareaProps) {
  const { control } = useFormContext()

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("space-y-1", className)}>
          {label && <label className="text-sm font-medium">{label}</label>}
          <textarea
            {...field}
            rows={rows}
            placeholder={placeholder}
            disabled={disabled}
            className={cn(
              "flex w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-xs placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
              error && "border-destructive"
            )}
          />
          {error && <p className="text-xs text-destructive">{error.message}</p>}
        </div>
      )}
    />
  )
}
