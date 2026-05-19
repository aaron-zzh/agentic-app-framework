/**
 * 注册页——邮箱注册 + 验证码验证
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { zodResolver } from "@hookform/resolvers/zod"

import { Button } from "@/components/ui/button"
import { Form } from "@/components/form/form"
import { FieldText } from "@/components/form/field-text"
import { paths } from "@/lib/constants/paths"
import { authApi } from "@/lib/api/auth"
import { useAuthStore } from "@/lib/store/auth-store"

const registerSchema = z
  .object({
    email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确"),
    password: z.string().min(6, "密码至少 6 位"),
    confirmPassword: z.string().min(1, "请确认密码"),
    nickname: z.string().optional()
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "两次密码不一致",
    path: ["confirmPassword"]
  })

type RegisterForm = z.infer<typeof registerSchema>

const verifySchema = z.object({
  code: z.string().min(1, "请输入验证码")
})

type VerifyForm = z.infer<typeof verifySchema>

export default function RegisterPage() {
  const router = useRouter()
  const { setTokens, setUser } = useAuthStore()
  const [step, setStep] = useState<"register" | "verify">("register")
  const [email, setEmail] = useState("")

  const registerMethods = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "", confirmPassword: "", nickname: "" }
  })

  const verifyMethods = useForm<VerifyForm>({
    resolver: zodResolver(verifySchema),
    defaultValues: { code: "" }
  })

  async function onRegister(data: RegisterForm) {
    await authApi.register(data.email, data.password, data.nickname || undefined)
    await authApi.sendCode(data.email, "register")
    setEmail(data.email)
    setStep("verify")
  }

  async function onVerify(data: VerifyForm) {
    const result = await authApi.verifyEmail(email, data.code)
    setTokens(result.accessToken, result.refreshToken)
    const user = await authApi.me()
    setUser(user)
    router.push(paths.workspace.root)
  }

  if (step === "verify") {
    return (
      <div className="w-full max-w-sm space-y-6">
        <div>
          <h2 className="font-bold text-2xl">验证邮箱</h2>
          <p className="mt-1 text-muted-foreground text-sm">
            验证码已发送至 {email}
          </p>
        </div>

        <Form methods={verifyMethods} onSubmit={onVerify}>
          <FieldText name="code" label="验证码" placeholder="输入 6 位验证码" />
          <Button
            type="submit"
            className="w-full"
            disabled={verifyMethods.formState.isSubmitting}
          >
            {verifyMethods.formState.isSubmitting ? "验证中..." : "验证并登录"}
          </Button>
        </Form>
      </div>
    )
  }

  return (
    <div className="w-full max-w-sm space-y-6">
      <div>
        <h2 className="font-bold text-2xl">注册</h2>
        <p className="mt-1 text-muted-foreground text-sm">创建你的 AAF 账号</p>
      </div>

      <Form methods={registerMethods} onSubmit={onRegister}>
        <FieldText name="email" label="邮箱" type="email" placeholder="your@email.com" />
        <FieldText name="nickname" label="昵称" placeholder="可选" />
        <FieldText name="password" label="密码" type="password" placeholder="至少 6 位" />
        <FieldText
          name="confirmPassword"
          label="确认密码"
          type="password"
          placeholder="再次输入密码"
        />
        <Button
          type="submit"
          className="w-full"
          disabled={registerMethods.formState.isSubmitting}
        >
          {registerMethods.formState.isSubmitting ? "注册中..." : "注册"}
        </Button>
      </Form>

      <p className="text-center text-muted-foreground text-sm">
        已有账号？{" "}
        <Link href={paths.auth.login} className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  )
}
