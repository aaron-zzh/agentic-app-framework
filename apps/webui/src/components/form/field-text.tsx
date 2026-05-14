/**
 * Field.Text——文本输入表单控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface FieldTextProps {
  name: string
  label?: string
  placeholder?: string
  type?: "text" | "email" | "password"
  className?: string
  disabled?: boolean
}

export function FieldText({ name, label, placeholder, type = "text", className, disabled }: FieldTextProps) {
  const { control } = useFormContext()

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("space-y-1", className)}>
          {label && <label className="text-sm font-medium">{label}</label>}
          <input
            {...field}
            type={type}
            placeholder={placeholder}
            disabled={disabled}
            className={cn(
              "flex h-9 w-full rounded-md border bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
              error && "border-destructive"
            )}
          />
          {error && <p className="text-xs text-destructive">{error.message}</p>}
        </div>
      )}
    />
  )
}
