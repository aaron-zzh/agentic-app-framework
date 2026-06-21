/**
 * Field.Otp——RHF 验证码输入控件
 * @author AaronZZH & Kiro
 *
 * 特性：
 * - 4-8 位定长验证码（默认 6 位），自动切换下一格
 * - 完成自动提交（autoSubmit=true 时通过最近的 form.requestSubmit() 触发，走 Form 的 onSubmit）
 * - 默认仅允许数字（pattern=REGEXP_ONLY_DIGITS）
 * - 移动端自动填充（autoComplete="one-time-code"）
 *
 * @example
 * ```tsx
 * <Form methods={methods} onSubmit={onVerify}>
 *   <FieldOtp name="code" label="验证码" length={6} autoSubmit />
 * </Form>
 * ```
 */

"use client"

import { REGEXP_ONLY_DIGITS, REGEXP_ONLY_DIGITS_AND_CHARS } from "input-otp"
import { useId, useRef } from "react"
import { Controller, useFormContext } from "react-hook-form"

import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils/cn"

export interface FieldOtpProps {
  /** RHF 字段名 */
  name: string
  /** 标签文本 */
  label?: string
  /** 描述/帮助文本 */
  description?: string
  /** 验证码长度，4-8 位，默认 6 */
  length?: 4 | 5 | 6 | 7 | 8
  /** 输入完成后是否自动提交所属表单。需要 Form 组件包裹（渲染真实 form 标签） */
  autoSubmit?: boolean
  /** 输入完成回调（autoSubmit 之外的额外副作用） */
  onComplete?: (value: string) => void
  /** 是否允许字母（默认 false，仅数字） */
  allowAlpha?: boolean
  /** 容器类名 */
  className?: string
  /** slot 容器类名 */
  containerClassName?: string
  /** 禁用 */
  disabled?: boolean
  /** autoComplete，默认 "one-time-code" 触发短信验证码自动填充 */
  autoComplete?: string
}

export function FieldOtp({
  name,
  label,
  description,
  length = 6,
  autoSubmit = false,
  onComplete,
  allowAlpha = false,
  className,
  containerClassName,
  disabled,
  autoComplete = "one-time-code"
}: FieldOtpProps) {
  const { control } = useFormContext()
  const reactId = useId()
  const id = `field-otp-${reactId}`
  const containerRef = useRef<HTMLDivElement | null>(null)
  const pattern = allowAlpha ? REGEXP_ONLY_DIGITS_AND_CHARS : REGEXP_ONLY_DIGITS

  return (
    <div ref={containerRef} className={cn("flex flex-col items-center gap-1.5", className)}>
      {label && <Label htmlFor={id}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <InputOTP
              id={id}
              maxLength={length}
              value={field.value ?? ""}
              onChange={field.onChange}
              onBlur={field.onBlur}
              disabled={disabled}
              autoComplete={autoComplete}
              pattern={pattern}
              aria-invalid={!!error}
              containerClassName={containerClassName}
              onComplete={(value) => {
                onComplete?.(value)
                if (autoSubmit) {
                  // 通过最近的 <form> 触发原生提交，走 RHF handleSubmit 校验链路
                  containerRef.current?.closest("form")?.requestSubmit()
                }
              }}
            >
              <InputOTPGroup>
                {Array.from({ length }, (_, i) => (
                  <InputOTPSlot key={i} index={i} aria-invalid={!!error} />
                ))}
              </InputOTPGroup>
            </InputOTP>
            {description && <p className="text-muted-foreground text-xs">{description}</p>}
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}
