"use client"

/**
 * 手机验证码登录面板——手机号 + 短信验证码（登录即注册）
 * @author AaronZZH & Kiro
 */

import { zodResolver } from "@hookform/resolvers/zod"
import { useEffect, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { TermsConsentCheckbox } from "@/components/common/TermsConsentCheckbox"
import { FieldOtp } from "@/components/form/field-otp"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
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

export function PhoneLoginPanel({
  onSuccess
}: {
  onSuccess: (accessToken: string, refreshToken: string, isNewUser?: boolean) => void
}) {
  const [step, setStep] = useState<"phone" | "code">("phone")
  const [phone, setPhone] = useState("")
  const [countdown, setCountdown] = useState(0)
  const [termsAgreed, setTermsAgreed] = useState(false)
  const captchaVerifyParamRef = useRef<string>("")

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

  async function onSendCode(data: PhoneLoginForm) {
    if (!termsAgreed) return
    try {
      await sendCode(data.phone, captcha.enabled ? captchaVerifyParamRef.current : undefined)
      setPhone(data.phone)
      setStep("code")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "发送失败，请重试"
      phoneMethods.setError("root", { message: msg })
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  const submitRef = useRef<(() => void) | null>(null)
  submitRef.current = phoneMethods.handleSubmit(onSendCode)

  const captcha = useEsaCaptcha({
    elementId: "phone-code-captcha",
    buttonId: "phone-code-send-btn",
    onVerified: (param) => {
      captchaVerifyParamRef.current = param
      submitRef.current?.()
    }
  })

  async function onVerify(data: PhoneVerifyForm) {
    if (!termsAgreed) return
    try {
      const result = await authApi.loginByPhone(phone, data.code, readRefCode())
      // 仅新用户注册场景下清除 refCode，避免老用户登录把别人的 refCode 错误清掉
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
      await sendCode(phone, captcha.enabled ? captchaVerifyParamRef.current : undefined)
    } catch (_err) {
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

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
            onClick={() => setStep("phone")}
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

  return (
    <Form
      methods={phoneMethods}
      onSubmit={captcha.enabled ? undefined : onSendCode}
      className="space-y-5"
    >
      <FieldText name="phone" label="手机号" type="tel" placeholder="请输入手机号" />

      {captcha.enabled && <div id="phone-code-captcha" />}

      <TermsConsentCheckbox checked={termsAgreed} onCheckedChange={setTermsAgreed} />

      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${phoneMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {phoneMethods.formState.errors.root?.message ?? " "}
      </p>

      <Button
        id="phone-code-send-btn"
        type={captcha.buttonType}
        className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
        disabled={phoneMethods.formState.isSubmitting}
        onClick={async (e) => {
          e.preventDefault()
          const valid = await phoneMethods.trigger("phone")
          if (!valid) return
          if (!termsAgreed) {
            notify.warning("请先阅读并同意服务条款与隐私政策")
            return
          }
          notify.info("手机验证码功能开发中，敬请期待")
        }}
      >
        {phoneMethods.formState.isSubmitting ? "发送中..." : "发送验证码"}
      </Button>
    </Form>
  )
}
