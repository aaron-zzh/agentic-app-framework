/**
 * 注册页——邮箱密码注册 + 手机验证码注册（登录即注册）
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）：
 *   - 注册按钮防止刷邮件 / 占用邮箱
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { TermsConsentCheckbox } from "@/components/common/TermsConsentCheckbox"
import { FieldOtp } from "@/components/form/field-otp"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { authApi } from "@/lib/api/rest/user/auth"
import { APP } from "@/lib/config"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"
import { useAuthStore } from "@/lib/store/auth-store"
import { clearRefCode, readRefCode } from "@/lib/utils/ref-code"

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

// ==================== 手机验证码注册面板（登录即注册） ====================

const phoneRegisterSchema = z.object({
  phone: z
    .string()
    .min(1, "请输入手机号")
    .regex(/^1[3-9]\d{9}$/, "手机号格式不正确")
})

const phoneRegisterVerifySchema = z.object({
  code: z.string().min(1, "请输入验证码").length(6, "验证码为 6 位数字")
})

type PhoneRegisterForm = z.infer<typeof phoneRegisterSchema>
type PhoneRegisterVerifyForm = z.infer<typeof phoneRegisterVerifySchema>

function PhoneRegisterPanel({
  onSuccess
}: {
  onSuccess: (accessToken: string, refreshToken: string) => void
}) {
  const [step, setStep] = useState<"phone" | "code">("phone")
  const [phone, setPhone] = useState("")
  const [countdown, setCountdown] = useState(0)
  const [termsAgreed, setTermsAgreed] = useState(false)
  const captchaVerifyParamRef = useRef<string>("")

  const phoneMethods = useForm<PhoneRegisterForm>({
    resolver: zodResolver(phoneRegisterSchema),
    defaultValues: { phone: "" }
  })

  const codeMethods = useForm<PhoneRegisterVerifyForm>({
    resolver: zodResolver(phoneRegisterVerifySchema),
    defaultValues: { code: "" }
  })

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

  async function onSendCode(data: PhoneRegisterForm) {
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
    elementId: "phone-register-captcha",
    buttonId: "phone-register-send-btn",
    onVerified: (param) => {
      captchaVerifyParamRef.current = param
      submitRef.current?.()
    }
  })

  async function onVerify(data: PhoneRegisterVerifyForm) {
    if (!termsAgreed) return
    try {
      const result = await authApi.loginByPhone(phone, data.code, readRefCode())
      clearRefCode()
      onSuccess(result.accessToken, result.refreshToken)
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
            className="h-11 w-full"
            disabled={codeMethods.formState.isSubmitting || !termsAgreed}
          >
            {codeMethods.formState.isSubmitting ? "验证中..." : "注册并登录"}
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
      {captcha.enabled && <div id="phone-register-captcha" />}
      <TermsConsentCheckbox checked={termsAgreed} onCheckedChange={setTermsAgreed} />
      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${phoneMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {phoneMethods.formState.errors.root?.message ?? " "}
      </p>
      <Button
        id="phone-register-send-btn"
        type={captcha.buttonType}
        className="h-11 w-full"
        disabled={phoneMethods.formState.isSubmitting}
        onClick={async (e) => {
          if (captcha.enabled) return // 验证码模式由 captcha 触发，不拦截
          e.preventDefault()
          const valid = await phoneMethods.trigger("phone")
          if (!valid) return
          if (!termsAgreed) {
            notify.warning("请先阅读并同意服务条款与隐私政策")
            return
          }
          void phoneMethods.handleSubmit(onSendCode)()
        }}
      >
        {phoneMethods.formState.isSubmitting ? "发送中..." : "发送验证码"}
      </Button>
    </Form>
  )
}

// ==================== 邮箱密码注册面板 ====================

function PasswordRegisterPanel({
  onSuccess,
  initialEmail
}: {
  onSuccess: (accessToken: string, refreshToken: string) => void
  initialEmail?: string
}) {
  const [step, setStep] = useState<"register" | "verify">("register")
  const [email, setEmail] = useState("")
  const captchaVerifyParamRef = useRef<string>("")
  const submitRef = useRef<(() => void) | null>(null)

  const registerMethods = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: initialEmail ?? "",
      password: "",
      confirmPassword: "",
      nickname: ""
    }
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
        captcha.enabled ? captchaVerifyParamRef.current : undefined,
        readRefCode()
      )
      setEmail(data.email)
      setStep("verify")
    } catch (err) {
      const msg = err instanceof Error ? err.message : "注册失败，请重试"
      registerMethods.setError("root", { message: msg })
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  submitRef.current = registerMethods.handleSubmit(onRegister)

  async function onVerify(data: VerifyForm) {
    try {
      const result = await authApi.verifyEmail(email, data.code, readRefCode())
      // 注册流程结束后清理 refCode，避免下次注册再被关联
      clearRefCode()
      onSuccess(result.accessToken, result.refreshToken)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "验证失败，请重试"
      verifyMethods.setError("root", { message: msg })
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

function RegisterPageInner() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const initialEmail = searchParams.get("email") ?? undefined
  const { setTokens, setUser } = useAuthStore()

  async function handleSuccess(accessToken: string, refreshToken: string) {
    setTokens(accessToken, refreshToken)
    const { user } = await authApi.me()
    setUser(user)
    router.push("/studio/welcome")
  }

  return (
    <div className="w-full max-w-sm space-y-6">
      <div>
        <h2 className="font-bold text-2xl">注册</h2>
        <p className="mt-1 text-muted-foreground text-sm">创建你的 {APP.name} 账号</p>
      </div>

      <Tabs defaultValue="phone">
        <TabsList className="w-full">
          <TabsTrigger value="phone" className="flex-1">
            手机
          </TabsTrigger>
          <TabsTrigger value="email" className="flex-1">
            邮箱
          </TabsTrigger>
        </TabsList>
        <TabsContent value="phone" className="pt-4">
          <PhoneRegisterPanel onSuccess={handleSuccess} />
        </TabsContent>
        <TabsContent value="email" className="pt-4">
          <PasswordRegisterPanel onSuccess={handleSuccess} initialEmail={initialEmail} />
        </TabsContent>
      </Tabs>

      <p className="text-center text-muted-foreground text-sm">
        已有账号？{" "}
        <Link href={paths.auth.login} className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  )
}

export default function RegisterPage() {
  // useSearchParams 需要 Suspense 边界才能在 build 时静态化通过
  return (
    <Suspense fallback={null}>
      <RegisterPageInner />
    </Suspense>
  )
}
