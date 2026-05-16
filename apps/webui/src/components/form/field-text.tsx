/**
 * Field.Text——RHF 封装（复用 entity-engine TextInput）
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { TextInput } from "@/features/entity-engine/components/fields"
import type { DataFieldDef } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

export interface FieldTextProps {
  name: string
  label?: string
  placeholder?: string
  type?: "text" | "email" | "password"
  className?: string
  disabled?: boolean
}

export function FieldText({
  name,
  label,
  placeholder,
  type = "text",
  className,
  disabled
}: FieldTextProps) {
  const { control } = useFormContext()

  const fieldDef: DataFieldDef = { type: "text", name, label, placeholder }

  return (
    <div className={cn(className)}>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <TextInput
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
