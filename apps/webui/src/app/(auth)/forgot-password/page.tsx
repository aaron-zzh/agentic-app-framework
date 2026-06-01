/**
 * 忘记密码页——发送验证码 + 重置密码
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"

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

  const emailMethods = useForm<EmailForm>({
    resolver: zodResolver(emailSchema),
    defaultValues: { email: "" }
  })

  const resetMethods = useForm<ResetForm>({
    resolver: zodResolver(resetSchema),
    defaultValues: { code: "", newPassword: "", confirmPassword: "" }
  })

  async function onSendCode(data: EmailForm) {
    await authApi.sendCode(data.email, "reset")
    setEmail(data.email)
    setStep("reset")
  }

  async function onReset(data: ResetForm) {
    await authApi.resetPassword(email, data.code, data.newPassword)
    router.push(paths.auth.login)
  }

  if (step === "reset") {
    return (
      <div className="w-full max-w-sm space-y-6">
        <div>
          <h2 className="font-bold text-2xl">重置密码</h2>
          <p className="mt-1 text-muted-foreground text-sm">验证码已发送至 {email}</p>
        </div>

        <Form methods={resetMethods} onSubmit={onReset}>
          <FieldText name="code" label="验证码" placeholder="输入 6 位验证码" />
          <FieldText name="newPassword" label="新密码" type="password" placeholder="至少 6 位" />
          <FieldText
            name="confirmPassword"
            label="确认密码"
            type="password"
            placeholder="再次输入密码"
          />
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

      <Form methods={emailMethods} onSubmit={onSendCode}>
        <FieldText name="email" label="邮箱" type="email" placeholder="your@email.com" />
        <Button type="submit" className="w-full" disabled={emailMethods.formState.isSubmitting}>
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
