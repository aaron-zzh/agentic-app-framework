/**
 * Field.Checkbox——RHF 封装（复用 entity-engine CheckboxInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { CheckboxInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

export interface FieldCheckboxProps {
  name: string
  label?: string
  className?: string
  disabled?: boolean
}

export function FieldCheckbox({ name, label, className, disabled }: FieldCheckboxProps) {
  const { control } = useFormContext()

  const fieldDef: DataFieldDef = { type: "checkbox", name, label }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <CheckboxInput
            name={name}
            value={field.value ?? false}
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
