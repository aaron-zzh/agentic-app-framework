/**
 * Field.Text——RHF 文本输入控件
 * @author AaronZZH & Kiro
 */

"use client"

import { Eye, EyeOff } from "lucide-react"
import { useState } from "react"
import { Controller, useFormContext } from "react-hook-form"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils/cn"

export interface FieldTextProps {
  name: string
  label?: string
  description?: string
  placeholder?: string
  type?: "text" | "email" | "password" | "tel" | "url" | "number"
  className?: string
  disabled?: boolean
  autoComplete?: string
}

export function FieldText({
  name,
  label,
  description,
  placeholder,
  type = "text",
  className,
  disabled,
  autoComplete
}: FieldTextProps) {
  const { control } = useFormContext()
  const [showPassword, setShowPassword] = useState(false)
  const isPassword = type === "password"

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <div className="relative">
              <Input
                id={name}
                type={isPassword ? (showPassword ? "text" : "password") : type}
                placeholder={placeholder}
                disabled={disabled}
                autoComplete={autoComplete}
                aria-invalid={!!error}
                className={cn(isPassword && "pr-9")}
                {...field}
              />
              {isPassword && (
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-2.5 text-muted-foreground hover:text-foreground"
                  aria-label={showPassword ? "隐藏密码" : "显示密码"}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              )}
            </div>
            {description && <p className="text-muted-foreground text-xs">{description}</p>}
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}
