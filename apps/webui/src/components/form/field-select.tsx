/**
 * Field.Select——下拉选择
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface SelectOption {
  label: string
  value: string
}

export interface FieldSelectProps {
  name: string
  label?: string
  options: SelectOption[]
  placeholder?: string
  className?: string
  disabled?: boolean
}

export function FieldSelect({ name, label, options, placeholder, className, disabled }: FieldSelectProps) {
  const { control } = useFormContext()

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("space-y-1", className)}>
          {label && <label className="text-sm font-medium">{label}</label>}
          <select
            {...field}
            disabled={disabled}
            className={cn(
              "flex h-9 w-full rounded-md border bg-transparent px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
              error && "border-destructive"
            )}
          >
            {placeholder && <option value="">{placeholder}</option>}
            {options.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          {error && <p className="text-xs text-destructive">{error.message}</p>}
        </div>
      )}
    />
  )
}
