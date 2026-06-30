"use client"

/**
 * 手机验证码登录面板——手机号 + 短信验证码（登录即注册）
 *
 * 第一步（输入手机号）：不含提交按钮和 captcha，由父层 LoginContent 统一持有。
 *   通过 ref.triggerSend(captchaParam?) 触发发送短信。
 * 第二步（输入短信验证码）：自管按钮（登录）+ 重发链接。
 * @author AaronZZH & Kiro
 */

import { zodResolver } from "@hookform/resolvers/zod"
import { type RefObject, useEffect, useImperativeHandle, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { TermsConsentCheckbox } from "@/components/common/TermsConsentCheckbox"
import { FieldOtp } from "@/components/form/field-otp"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { notify } from "@/lib/notification"
import { clearRefCode, readRefCode } from "@/lib/utils/ref-code"

const phoneLoginSchema = z.object({
  phone: z
    .string()
    .min(1, "请输入手机号")
    .regex(/^1[3-9]\d{9}$/, "手机号格式不正确")
})

const phoneVerifySchema = z.object({
  code: z.string().min(1, "请输入验证码").length(6, "验证码为 6 位数字")
})

type PhoneLoginForm = z.infer<typeof phoneLoginSchema>
type PhoneVerifyForm = z.infer<typeof phoneVerifySchema>

export interface PhoneLoginPanelRef {
  /** 当前步骤，父层据此切换按钮文字 */
  step: "phone" | "code"
  /** 第一步：触发发送短信；captchaParam 由父层 captcha 回调传入 */
  triggerSend: (captchaParam?: string) => Promise<void>
  /** 提交前校验表单，返回是否通过 */
  validate: () => Promise<boolean>
  /** 服务条款是否已同意（父层按钮需要据此 disabled） */
  termsAgreed: boolean
}

export function PhoneLoginPanel({
  onSuccess,
  onStepChange,
  onTermsChange,
  ref
}: {
  onSuccess: (accessToken: string, refreshToken: string, isNewUser?: boolean) => void
  onStepChange?: (step: "phone" | "code") => void
  onTermsChange?: (agreed: boolean) => void
  ref?: RefObject<PhoneLoginPanelRef | null>
}) {
  const [step, setStep] = useState<"phone" | "code">("phone")

  function updateStep(s: "phone" | "code") {
    setStep(s)
    onStepChange?.(s)
  }

  const [phone, setPhone] = useState("")
  const [countdown, setCountdown] = useState(0)
  const [termsAgreed, setTermsAgreed] = useState(false)

  function updateTerms(v: boolean) {
    setTermsAgreed(v)
    onTermsChange?.(v)
  }

  const phoneMethods = useForm<PhoneLoginForm>({
    resolver: zodResolver(phoneLoginSchema),
    defaultValues: { phone: "" }
  })

  const codeMethods = useForm<PhoneVerifyForm>({
    resolver: zodResolver(phoneVerifySchema),
    defaultValues: { code: "" }
  })

  // 倒计时
  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  async function sendCode(phoneVal: string, captchaParam?: string) {
    await authApi.sendSmsCode(phoneVal, "login", captchaParam)
    setCountdown(60)
    notify.success("验证码已发送，请查收短信")
  }

  async function doSend(data: PhoneLoginForm, captchaParam?: string) {
    if (!termsAgreed) {
      notify.warning("请先阅读并同意服务条款与隐私政策")
      return
    }
    try {
      await sendCode(data.phone, captchaParam)
      setPhone(data.phone)
      updateStep("code")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "发送失败，请重试"
      phoneMethods.setError("root", { message: msg })
    }
  }

  // 用 ref 让 triggerSend 始终拿到最新的 termsAgreed（闭包问题）
  const doSendRef = useRef(doSend)
  doSendRef.current = doSend

  useImperativeHandle(ref, () => ({
    get step() {
      return step
    },
    triggerSend: async (captchaParam?: string) => {
      await phoneMethods.handleSubmit((data) => doSendRef.current(data, captchaParam))()
    },
    validate: () => phoneMethods.trigger("phone"),
    get termsAgreed() {
      return termsAgreed
    }
  }))

  async function onVerify(data: PhoneVerifyForm) {
    if (!termsAgreed) return
    try {
      const result = await authApi.loginByPhone(phone, data.code, readRefCode())
      if (result.isNewUser) clearRefCode()
      onSuccess(result.accessToken, result.refreshToken, result.isNewUser)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "验证失败，请重试"
      codeMethods.setError("root", { message: msg })
    }
  }

  async function onResend() {
    if (countdown > 0 || !termsAgreed) return
    try {
      await sendCode(phone)
    } catch (_err) {}
  }

  // ── 第二步：输入短信验证码（自管） ──
  if (step === "code") {
    return (
      <div className="space-y-5">
        <p className="text-muted-foreground text-sm">
          验证码已发送至 <span className="font-medium text-foreground">{phone}</span>
        </p>
        <Form methods={codeMethods} onSubmit={onVerify} className="space-y-5">
          <FieldOtp name="code" length={6} autoSubmit />

          <p
            className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${codeMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
          >
            {codeMethods.formState.errors.root?.message ?? " "}
          </p>

          <Button
            type="submit"
            className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
            disabled={codeMethods.formState.isSubmitting || !termsAgreed}
          >
            {codeMethods.formState.isSubmitting ? "验证中..." : "登录"}
          </Button>
        </Form>

        <div className="flex items-center justify-between text-sm">
          <button
            type="button"
            className="text-muted-foreground hover:text-primary"
            onClick={() => updateStep("phone")}
          >
            修改手机号
          </button>
          <button
            type="button"
            className={`${countdown > 0 || !termsAgreed ? "cursor-not-allowed text-muted-foreground" : "text-primary hover:underline"}`}
            disabled={countdown > 0 || !termsAgreed}
            onClick={onResend}
          >
            {countdown > 0 ? `重新发送 (${countdown}s)` : "重新发送"}
          </button>
        </div>
      </div>
    )
  }

  // ── 第一步：输入手机号（按钮由父层提供） ──
  return (
    <Form methods={phoneMethods} onSubmit={undefined} className="space-y-5">
      <FieldText name="phone" label="手机号" type="tel" placeholder="请输入手机号" />

      <TermsConsentCheckbox checked={termsAgreed} onCheckedChange={updateTerms} />

      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${phoneMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {phoneMethods.formState.errors.root?.message ?? " "}
      </p>
    </Form>
  )
}
