/**
 * 注册页——邮箱密码注册（手机号注册已合并到登录即注册流程）
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）：
 *   - 注册按钮防止刷邮件 / 占用邮箱
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"
import { useAuthStore } from "@/lib/store/auth-store"

// ==================== 邮箱密码注册 ====================

const registerSchema = z
  .object({
    email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确"),
    password: z.string().min(8, "密码至少 8 位").max(32, "密码最多 32 位"),
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

// ==================== 邮箱密码注册面板 ====================

function PasswordRegisterPanel({
  onSuccess
}: {
  onSuccess: (accessToken: string, refreshToken: string) => void
}) {
  const [step, setStep] = useState<"register" | "verify">("register")
  const [email, setEmail] = useState("")
  const captchaVerifyParamRef = useRef<string>("")
  const submitRef = useRef<(() => void) | null>(null)

  const registerMethods = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "", confirmPassword: "", nickname: "" }
  })

  const verifyMethods = useForm<VerifyForm>({
    resolver: zodResolver(verifySchema),
    defaultValues: { code: "" }
  })

  const captcha = useEsaCaptcha({
    elementId: "password-register-captcha",
    buttonId: "password-register-btn",
    onVerified: (param) => {
      captchaVerifyParamRef.current = param
      submitRef.current?.()
    }
  })

  async function onRegister(data: RegisterForm) {
    try {
      await authApi.register(
        data.email,
        data.password,
        data.nickname || undefined,
        captcha.enabled ? captchaVerifyParamRef.current : undefined
      )
      setEmail(data.email)
      setStep("verify")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "注册失败，请重试"
      registerMethods.setError("root", { message: msg })
      notify.error(msg)
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  submitRef.current = registerMethods.handleSubmit(onRegister)

  async function onVerify(data: VerifyForm) {
    try {
      const result = await authApi.verifyEmail(email, data.code)
      onSuccess(result.accessToken, result.refreshToken)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "验证失败，请重试"
      verifyMethods.setError("root", { message: msg })
      notify.error(msg)
    }
  }

  if (step === "verify") {
    return (
      <div className="space-y-5">
        <p className="text-muted-foreground text-sm">
          验证码已发送至 <span className="font-medium text-foreground">{email}</span>
        </p>
        <Form methods={verifyMethods} onSubmit={onVerify} className="space-y-5">
          <FieldText
            name="code"
            label="验证码"
            placeholder="输入 6 位验证码"
            autoComplete="one-time-code"
          />
          <p
            className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${verifyMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
          >
            {verifyMethods.formState.errors.root?.message ?? " "}
          </p>
          <Button
            type="submit"
            className="h-11 w-full"
            disabled={verifyMethods.formState.isSubmitting}
          >
            {verifyMethods.formState.isSubmitting ? "验证中..." : "验证并登录"}
          </Button>
        </Form>
        <button
          type="button"
          className="text-muted-foreground text-sm hover:text-primary"
          onClick={() => setStep("register")}
        >
          返回修改信息
        </button>
      </div>
    )
  }

  return (
    <Form
      methods={registerMethods}
      onSubmit={captcha.enabled ? undefined : onRegister}
      className="space-y-4"
    >
      <FieldText name="email" label="邮箱" type="email" placeholder="your@email.com" />
      <FieldText name="nickname" label="昵称" placeholder="可选" />
      <FieldText name="password" label="密码" type="password" placeholder="至少 8 位" />
      <FieldText
        name="confirmPassword"
        label="确认密码"
        type="password"
        placeholder="再次输入密码"
      />

      {captcha.enabled && <div id="password-register-captcha" />}

      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${registerMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {registerMethods.formState.errors.root?.message ?? " "}
      </p>
      <Button
        id="password-register-btn"
        type={captcha.buttonType}
        className="h-11 w-full"
        disabled={registerMethods.formState.isSubmitting}
      >
        {registerMethods.formState.isSubmitting ? "注册中..." : "注册"}
      </Button>
    </Form>
  )
}

// ==================== 注册页主体 ====================

export default function RegisterPage() {
  const router = useRouter()
  const { setTokens, setUser } = useAuthStore()

  async function handleSuccess(accessToken: string, refreshToken: string) {
    setTokens(accessToken, refreshToken)
    const { user } = await authApi.me()
    setUser(user)
    router.push(paths.workspace.root)
  }

  return (
    <div className="w-full max-w-sm space-y-6">
      <div>
        <h2 className="font-bold text-2xl">注册</h2>
        <p className="mt-1 text-muted-foreground text-sm">
          创建你的 AAF 账号；如需手机号注册，请直接前往
          <Link href={paths.auth.login} className="text-primary hover:underline">
            登录页
          </Link>
          通过手机验证码登录即注册。
        </p>
      </div>

      <PasswordRegisterPanel onSuccess={handleSuccess} />

      <p className="text-center text-muted-foreground text-sm">
        已有账号？{" "}
        <Link href={paths.auth.login} className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  )
}
