/**
 * 忘记密码页——支持邮箱和手机号两种方式重置密码
 * 自动识别输入内容：邮箱发邮件验证码，手机号发短信验证码
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldOtp } from "@/components/form/field-otp"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"

/** 判断是手机号还是邮箱 */
function detectInputType(value: string): "phone" | "email" | "unknown" {
  if (/^1[3-9]\d{9}$/.test(value)) return "phone"
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "email"
  return "unknown"
}

const accountSchema = z.object({
  account: z
    .string()
    .min(1, "请输入邮箱或手机号")
    .refine((v) => detectInputType(v) !== "unknown", "请输入有效的邮箱或手机号")
})

const resetSchema = z
  .object({
    code: z.string().min(1, "请输入验证码"),
    newPassword: z.string().min(6, "密码至少 6 位"),
    confirmPassword: z.string().min(1, "请确认密码")
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: "两次密码不一致",
    path: ["confirmPassword"]
  })

type AccountForm = z.infer<typeof accountSchema>
type ResetForm = z.infer<typeof resetSchema>

export default function ForgotPasswordPage() {
  const router = useRouter()
  const [step, setStep] = useState<"account" | "reset">("account")
  const [account, setAccount] = useState("")
  const [accountType, setAccountType] = useState<"email" | "phone">("email")
  const captchaVerifyParamRef = useRef<string>("")
  const submitRef = useRef<(() => void) | null>(null)

  const accountMethods = useForm<AccountForm>({
    resolver: zodResolver(accountSchema),
    defaultValues: { account: "" }
  })

  const resetMethods = useForm<ResetForm>({
    resolver: zodResolver(resetSchema),
    defaultValues: { code: "", newPassword: "", confirmPassword: "" }
  })

  const captcha = useEsaCaptcha({
    elementId: "forgot-password-captcha",
    buttonId: "forgot-password-send-btn",
    onVerified: (param) => {
      captchaVerifyParamRef.current = param
      submitRef.current?.()
    }
  })

  async function onSendCode(data: AccountForm) {
    const type = detectInputType(data.account) as "email" | "phone"
    try {
      if (type === "phone") {
        await authApi.sendSmsCode(
          data.account,
          "reset",
          captcha.enabled ? captchaVerifyParamRef.current : undefined
        )
      } else {
        await authApi.sendEmailCode(
          data.account,
          "reset",
          captcha.enabled ? captchaVerifyParamRef.current : undefined
        )
      }
      setAccount(data.account)
      setAccountType(type)
      setStep("reset")
      notify.success(type === "phone" ? "短信验证码已发送" : "验证码已发送，请查收邮件")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "发送失败，请重试"
      accountMethods.setError("root", { message: msg })
      notify.error(msg)
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  submitRef.current = accountMethods.handleSubmit(onSendCode)

  async function onReset(data: ResetForm) {
    try {
      if (accountType === "phone") {
        await authApi.resetPasswordByPhone(account, data.code, data.newPassword)
      } else {
        await authApi.resetPassword(account, data.code, data.newPassword)
      }
      notify.success("密码重置成功，请重新登录")
      router.push(paths.auth.login)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "重置失败，请重试"
      resetMethods.setError("root", { message: msg })
      notify.error(msg)
    }
  }

  if (step === "reset") {
    const hint = accountType === "phone" ? `短信已发送至 ${account}` : `验证码已发送至 ${account}`
    return (
      <div className="w-full max-w-sm space-y-6">
        <div>
          <h2 className="font-bold text-2xl">重置密码</h2>
          <p className="mt-1 text-muted-foreground text-sm">{hint}</p>
        </div>

        <Form methods={resetMethods} onSubmit={onReset}>
          <FieldOtp name="code" length={6} autoSubmit />
          <FieldText name="newPassword" label="新密码" type="password" placeholder="至少 6 位" />
          <FieldText
            name="confirmPassword"
            label="确认密码"
            type="password"
            placeholder="再次输入密码"
          />
          <p
            className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${resetMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
          >
            {resetMethods.formState.errors.root?.message ?? " "}
          </p>
          <Button type="submit" className="w-full" disabled={resetMethods.formState.isSubmitting}>
            {resetMethods.formState.isSubmitting ? "重置中..." : "重置密码"}
          </Button>
        </Form>

        <p className="text-center text-muted-foreground text-sm">
          <Link href={paths.auth.login} className="text-primary hover:underline">
            返回登录
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="w-full max-w-sm space-y-6">
      <div>
        <h2 className="font-bold text-2xl">忘记密码</h2>
        <p className="mt-1 text-muted-foreground text-sm">输入邮箱或手机号接收验证码</p>
      </div>

      <Form methods={accountMethods} onSubmit={captcha.enabled ? undefined : onSendCode}>
        <FieldText name="account" label="邮箱 / 手机号" placeholder="your@email.com 或 138xxxx" />

        {captcha.enabled && <div id="forgot-password-captcha" />}

        <p
          className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${accountMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
        >
          {accountMethods.formState.errors.root?.message ?? " "}
        </p>
        <Button
          id="forgot-password-send-btn"
          type={captcha.buttonType}
          className="w-full"
          disabled={accountMethods.formState.isSubmitting}
        >
          {accountMethods.formState.isSubmitting ? "发送中..." : "发送验证码"}
        </Button>
      </Form>

      <p className="text-center text-muted-foreground text-sm">
        想起密码了？{" "}
        <Link href={paths.auth.login} className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  )
}
