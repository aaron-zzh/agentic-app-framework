/**
 * 用户协议同意复选框——登录/注册场景的合规组件
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * const [agreed, setAgreed] = useState(false)
 * <TermsConsentCheckbox checked={agreed} onCheckedChange={setAgreed} />
 * <Button disabled={!agreed}>登录</Button>
 * ```
 */

"use client"

import Link from "next/link"
import { useId } from "react"
import { Checkbox } from "@/components/ui/checkbox"
import { cn } from "@/lib/utils/cn"

export interface TermsConsentCheckboxProps {
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  disabled?: boolean
  className?: string
  /** 用户协议链接，默认 /terms */
  termsHref?: string
  /** 隐私政策链接，默认 /privacy */
  privacyHref?: string
}

export function TermsConsentCheckbox({
  checked,
  onCheckedChange,
  disabled,
  className,
  termsHref = "/terms",
  privacyHref = "/privacy"
}: TermsConsentCheckboxProps) {
  const id = useId()
  return (
    <div className={cn("flex items-start gap-2", className)}>
      <Checkbox
        id={id}
        checked={checked}
        onCheckedChange={(c) => onCheckedChange(c === true)}
        disabled={disabled}
        className="mt-0.5"
      />
      <label
        htmlFor={id}
        className="cursor-pointer select-none text-muted-foreground text-xs leading-relaxed"
      >
        我已阅读并同意
        <Link
          href={termsHref}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary hover:underline"
        >
          《用户协议》
        </Link>
        和
        <Link
          href={privacyHref}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary hover:underline"
        >
          《隐私政策》
        </Link>
      </label>
    </div>
  )
}
