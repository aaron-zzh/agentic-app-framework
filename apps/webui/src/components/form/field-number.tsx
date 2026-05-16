/**
 * Field.Number——RHF 封装（复用 entity-engine NumberInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { NumberInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef } from "@/features/entity-engine/types"
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

export function FieldNumber({
  name,
  label,
  placeholder,
  min,
  max,
  step,
  className,
  disabled
}: FieldNumberProps) {
  const { control } = useFormContext()

  const fieldDef: DataFieldDef = { type: "number", name, label, placeholder, min, max, step }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <NumberInput
            name={name}
            value={field.value}
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
