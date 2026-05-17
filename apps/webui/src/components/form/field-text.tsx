/**
 * Field.Text——RHF 文本输入控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
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
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <Input
              id={name}
              type={type}
              placeholder={placeholder}
              disabled={disabled}
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
