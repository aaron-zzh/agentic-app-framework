/**
 * Field.Select——RHF 封装（复用 entity-engine SelectInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { SelectInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef, SelectOption } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

export interface FieldSelectProps {
  name: string
  label?: string
  options: SelectOption[]
  placeholder?: string
  className?: string
  disabled?: boolean
}

export function FieldSelect({
  name,
  label,
  options,
  placeholder,
  className,
  disabled
}: FieldSelectProps) {
  const { control } = useFormContext()

  const fieldDef: DataFieldDef = { type: "select", name, label, placeholder, options }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <SelectInput
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
