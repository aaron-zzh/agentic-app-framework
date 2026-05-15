/**
 * Field.Textarea——RHF 封装（复用 entity-engine TextareaInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { TextareaInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef } from "@/features/entity-engine/types"
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

  const fieldDef: DataFieldDef = { type: "textarea", name, label, placeholder, rows }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <TextareaInput
            name={name}
            value={field.value ?? ""}
            onChange={field.onChange}
            error={error?.message}
            disabled={disabled}
            field={fieldDef}
          />
        )}
      />
    </div>
  )
}
