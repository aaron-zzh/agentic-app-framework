/**
 * InputOTP——基于 input-otp 库的验证码输入组件
 * @author shadcn / AaronZZH & Kiro
 *
 * 适用场景：邮箱/短信验证码、TOTP、PIN 码等定长字符输入
 * 特性：自动切换下一格、键盘导航、整段粘贴、移动端 one-time-code 自动填充
 *
 * @example
 * ```tsx
 * <InputOTP maxLength={6} value={value} onChange={setValue}>
 *   <InputOTPGroup>
 *     <InputOTPSlot index={0} />
 *     <InputOTPSlot index={1} />
 *     <InputOTPSlot index={2} />
 *     <InputOTPSlot index={3} />
 *     <InputOTPSlot index={4} />
 *     <InputOTPSlot index={5} />
 *   </InputOTPGroup>
 * </InputOTP>
 * ```
 */

"use client"

import { OTPInput, OTPInputContext } from "input-otp"
import { Minus } from "lucide-react"
import * as React from "react"

import { cn } from "@/lib/utils/index"

function InputOTP({
  className,
  containerClassName,
  ...props
}: React.ComponentProps<typeof OTPInput> & {
  containerClassName?: string
}) {
  return (
    <OTPInput
      data-slot="input-otp"
      containerClassName={cn(
        "flex items-center gap-2 has-[:disabled]:opacity-50",
        containerClassName
      )}
      className={cn("disabled:cursor-not-allowed", className)}
      {...props}
    />
  )
}

function InputOTPGroup({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div data-slot="input-otp-group" className={cn("flex items-center", className)} {...props} />
  )
}

function InputOTPSlot({
  index,
  className,
  ...props
}: React.ComponentProps<"div"> & { index: number }) {
  const inputOTPContext = React.useContext(OTPInputContext)
  const slot = inputOTPContext?.slots[index]
  const char = slot?.char
  const hasFakeCaret = slot?.hasFakeCaret
  const isActive = slot?.isActive

  return (
    <div
      data-slot="input-otp-slot"
      data-active={isActive}
      className={cn(
        "relative flex h-10 w-10 items-center justify-center border-input border-y border-r text-base shadow-xs outline-none transition-all first:rounded-l-md first:border-l last:rounded-r-md aria-invalid:border-destructive data-[active=true]:z-10 data-[active=true]:border-ring data-[active=true]:ring-3 data-[active=true]:ring-ring/50 data-[active=true]:aria-invalid:border-destructive data-[active=true]:aria-invalid:ring-destructive/20 dark:bg-input/30 dark:data-[active=true]:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    >
      {char}
      {hasFakeCaret && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="h-4 w-px animate-caret-blink bg-foreground duration-1000" />
        </div>
      )}
    </div>
  )
}

function InputOTPSeparator({ ...props }: React.ComponentProps<"div">) {
  return (
    // 纯视觉分隔符——用 aria-hidden 让屏幕阅读器跳过；
    // 不用 role="separator"（那是可聚焦的 widget role，会要求 tabIndex+aria-orientation 等）
    <div data-slot="input-otp-separator" aria-hidden="true" {...props}>
      <Minus className="size-3" />
    </div>
  )
}

export { InputOTP, InputOTPGroup, InputOTPSeparator, InputOTPSlot }
