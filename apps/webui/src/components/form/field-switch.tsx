/**
 * Field.Switch——RHF 开关控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { cn } from "@/lib/utils/cn"

export interface FieldSwitchProps {
  name: string
  label?: string
  description?: string
  className?: string
  disabled?: boolean
}

export function FieldSwitch({ name, label, description, className, disabled }: FieldSwitchProps) {
  const { control } = useFormContext()
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <div className={cn("flex flex-col gap-1.5", className)}>
          <div className="flex items-center gap-2">
            <Switch
              id={name}
              checked={field.value ?? false}
              onCheckedChange={field.onChange}
              disabled={disabled}
              aria-invalid={!!error}
            />
            {label && <Label htmlFor={name}>{label}</Label>}
          </div>
          {description && <p className="text-muted-foreground text-xs">{description}</p>}
          {error && <p className="text-destructive text-xs">{error.message}</p>}
        </div>
      )}
    />
  )
}
