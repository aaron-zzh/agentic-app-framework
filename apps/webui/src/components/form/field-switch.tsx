/**
 * Field.Switch——开关控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface FieldSwitchProps {
  name: string
  label?: string
  className?: string
  disabled?: boolean
}

export function FieldSwitch({ name, label, className, disabled }: FieldSwitchProps) {
  const { control } = useFormContext()

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("flex items-center gap-2", className)}>
          <button
            type="button"
            role="switch"
            aria-checked={field.value ?? false}
            onClick={() => !disabled && field.onChange(!field.value)}
            disabled={disabled}
            className={cn(
              "inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors disabled:cursor-not-allowed disabled:opacity-50",
              field.value ? "bg-primary" : "bg-input"
            )}
          >
            <span
              className={cn(
                "pointer-events-none block size-4 rounded-full bg-background shadow-sm transition-transform",
                field.value ? "translate-x-4" : "translate-x-0"
              )}
            />
          </button>
          {label && <label className="text-sm">{label}</label>}
          {error && <p className="text-destructive text-xs">{error.message}</p>}
        </div>
      )}
    />
  )
}
