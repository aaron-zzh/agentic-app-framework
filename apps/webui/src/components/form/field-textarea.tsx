/**
 * Field.Textarea——RHF 多行文本控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils/cn"

export interface FieldTextareaProps {
  name: string
  label?: string
  placeholder?: string
  rows?: number
  className?: string
  disabled?: boolean
}

export function FieldTextarea({
  name,
  label,
  placeholder,
  rows = 3,
  className,
  disabled
}: FieldTextareaProps) {
  const { control } = useFormContext()
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <Textarea
              id={name}
              placeholder={placeholder}
              disabled={disabled}
              rows={rows}
              aria-invalid={!!error}
              {...field}
              value={field.value ?? ""}
            />
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}
