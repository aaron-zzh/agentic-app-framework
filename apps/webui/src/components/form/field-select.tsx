/**
 * Field.Select——RHF 下拉选择控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Controller, useFormContext } from "react-hook-form"

import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { cn } from "@/lib/utils/cn"

export interface SelectOption {
  label: string
  value: string
}

export interface FieldSelectProps {
  name: string
  label?: string
  description?: string
  options: SelectOption[]
  placeholder?: string
  className?: string
  disabled?: boolean
}

export function FieldSelect({
  name,
  label,
  description,
  options,
  placeholder,
  className,
  disabled
}: FieldSelectProps) {
  const { control } = useFormContext()
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <Select
              value={field.value ?? ""}
              onValueChange={(v) => field.onChange(v ?? "")}
              disabled={disabled}
              items={options}
            >
              <SelectTrigger id={name} className="w-full" aria-invalid={!!error}>
                <SelectValue placeholder={placeholder ?? "请选择"} />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {options.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            {description && <p className="text-muted-foreground text-xs">{description}</p>}
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}
