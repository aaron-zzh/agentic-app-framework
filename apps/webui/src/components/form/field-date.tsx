/**
 * Field.Date——RHF 封装（复用 entity-engine DateInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { DateInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

export interface FieldDateProps {
  name: string
  label?: string
  includeTime?: boolean
  className?: string
  disabled?: boolean
}

export function FieldDate({ name, label, includeTime, className, disabled }: FieldDateProps) {
  const { control } = useFormContext()

  const fieldDef: DataFieldDef = { type: "date", name, label, includeTime }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <DateInput
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
