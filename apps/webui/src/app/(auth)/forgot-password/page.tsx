/**
 * 忘记密码页——发送验证码 + 重置密码
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）：
 *   - 发送验证码按钮（防止刷邮件、枚举邮箱）
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

const emailSchema = z.object({
  email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确")
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

type EmailForm = z.infer<typeof emailSchema>
type ResetForm = z.infer<typeof resetSchema>

export default function ForgotPasswordPage() {
  const router = useRouter()
  const [step, setStep] = useState<"email" | "reset">("email")
  const [email, setEmail] = useState("")
  const captchaVerifyParamRef = useRef<string>("")
  const submitRef = useRef<(() => void) | null>(null)

  const emailMethods = useForm<EmailForm>({
    resolver: zodResolver(emailSchema),
    defaultValues: { email: "" }
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

  async function onSendCode(data: EmailForm) {
    try {
      await authApi.sendEmailCode(
        data.email,
        "reset",
        captcha.enabled ? captchaVerifyParamRef.current : undefined
      )
      setEmail(data.email)
      setStep("reset")
      notify.success("验证码已发送，请查收邮件")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "发送失败，请重试"
      emailMethods.setError("root", { message: msg })
      notify.error(msg)
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  submitRef.current = emailMethods.handleSubmit(onSendCode)

  async function onReset(data: ResetForm) {
    try {
      await authApi.resetPassword(email, data.code, data.newPassword)
      router.push(paths.auth.login)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "重置失败，请重试"
      resetMethods.setError("root", { message: msg })
      notify.error(msg)
    }
  }

  if (step === "reset") {
    return (
      <div className="w-full max-w-sm space-y-6">
        <div>
          <h2 className="font-bold text-2xl">重置密码</h2>
          <p className="mt-1 text-muted-foreground text-sm">验证码已发送至 {email}</p>
        </div>

        <Form methods={resetMethods} onSubmit={onReset}>
          <FieldOtp name="code" length={6} />
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
        <p className="mt-1 text-muted-foreground text-sm">输入邮箱接收验证码</p>
      </div>

      <Form methods={emailMethods} onSubmit={captcha.enabled ? undefined : onSendCode}>
        <FieldText name="email" label="邮箱" type="email" placeholder="your@email.com" />

        {captcha.enabled && <div id="forgot-password-captcha" />}

        <p
          className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${emailMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
        >
          {emailMethods.formState.errors.root?.message ?? " "}
        </p>
        <Button
          id="forgot-password-send-btn"
          type={captcha.buttonType}
          className="w-full"
          disabled={emailMethods.formState.isSubmitting}
        >
          {emailMethods.formState.isSubmitting ? "发送中..." : "发送验证码"}
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
