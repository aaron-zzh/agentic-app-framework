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
  className?: string
  disabled?: boolean
}

export function FieldSwitch({ name, label, className, disabled }: FieldSwitchProps) {
  const { control } = useFormContext()
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <div className={cn("flex items-center gap-2", className)}>
          <Switch
            id={name}
            checked={field.value ?? false}
            onCheckedChange={field.onChange}
            disabled={disabled}
          />
          {label && <Label htmlFor={name}>{label}</Label>}
        </div>
      )}
    />
  )
}
