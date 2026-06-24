/**
 * 登录页——密码登录 / 验证码登录（Tab 切换）+ 第三方 OAuth 入口
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）：
 *   - 密码登录：登录按钮
 *   - 邮箱验证码登录：发送验证码按钮
 *   - 手机验证码登录：发送验证码按钮
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useCallback, useEffect, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { ConsentDialog } from "@/components/common/ConsentDialog"
import { TermsConsentCheckbox } from "@/components/common/TermsConsentCheckbox"
import { FieldOtp } from "@/components/form/field-otp"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ApiError } from "@/lib/api/errors"
import { type LegalDocument, legalApi } from "@/lib/api/rest/legal"
import { authApi } from "@/lib/api/rest/user/auth"
import { clearAxiosAuth, setAxiosAuth } from "@/lib/auth/utils"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"
import { type AuthUser, useAuthStore } from "@/lib/store/auth-store"
import { clearRefCode, readRefCode } from "@/lib/utils/ref-code"
import { LoginSuccessOverlay } from "./LoginSuccessOverlay"

// ==================== 密码登录表单 ====================

const loginSchema = z.object({
  username: z
    .string()
    .min(1, "请输入账号或邮箱")
    .min(4, "账号或邮箱长度为 4-200 位")
    .max(200, "账号或邮箱长度为 4-200 位")
    .refine(
      (value) => /^[A-Za-z0-9]+$/.test(value) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
      "请输入用户名或邮箱"
    ),
  password: z.string().min(1, "请输入密码")
})

type LoginForm = z.infer<typeof loginSchema>

// ==================== 验证码登录表单 ====================

const codeLoginSchema = z.object({
  email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确")
})

const codeVerifySchema = z.object({
  code: z.string().min(1, "请输入验证码").length(6, "验证码为 6 位数字")
})

type CodeLoginForm = z.infer<typeof codeLoginSchema>
type CodeVerifyForm = z.infer<typeof codeVerifySchema>

// ==================== 手机验证码登录表单 ====================

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

// ==================== 验证码登录子组件（预留，暂未挂载）====================

// function _CodeLoginPanel({
//   onSuccess
// }: {
//   onSuccess: (accessToken: string, refreshToken: string, isNewUser?: boolean) => void
// }) {
//   const [step, setStep] = useState<"email" | "code">("email")
//   const [email, setEmail] = useState("")
//   const [countdown, setCountdown] = useState(0)
//   /** 后端返回 AUTH_LOGIN_BAD_CREDENTIALS（code=1000000，邮箱未注册）时切到 true，引导跳到注册流程 */
//   const [notRegistered, setNotRegistered] = useState(false)
//   const captchaVerifyParamRef = useRef<string>("")

//   const emailMethods = useForm<CodeLoginForm>({
//     resolver: zodResolver(codeLoginSchema),
//     defaultValues: { email: "" }
//   })

//   const codeMethods = useForm<CodeVerifyForm>({
//     resolver: zodResolver(codeVerifySchema),
//     defaultValues: { code: "" }
//   })

//   // 倒计时
//   useEffect(() => {
//     if (countdown <= 0) return
//     const timer = setTimeout(() => setCountdown((c) => c - 1), 1000)
//     return () => clearTimeout(timer)
//   }, [countdown])

//   async function sendCode(emailVal: string, captchaParam?: string) {
//     await authApi.sendEmailCode(emailVal, "login", captchaParam)
//     setCountdown(60)
//     notify.success("验证码已发送，请查收邮件")
//   }

//   async function onSendCode(data: CodeLoginForm) {
//     try {
//       await sendCode(data.email, captcha.enabled ? captchaVerifyParamRef.current : undefined)
//       setEmail(data.email)
//       setStep("code")
//     } catch (err) {
//       const msg = err instanceof Error ? err.message : "发送失败，请重试"
//       emailMethods.setError("root", { message: msg })
//       notify.error(msg)
//     } finally {
//       captchaVerifyParamRef.current = ""
//       captcha.reset()
//     }
//   }

//   // 拿最新提交闭包，ESA 验证通过后调用
//   const submitRef = useRef<(() => void) | null>(null)
//   submitRef.current = emailMethods.handleSubmit(onSendCode)

//   const captcha = useEsaCaptcha({
//     elementId: "email-code-captcha",
//     buttonId: "email-code-send-btn",
//     onVerified: (param) => {
//       captchaVerifyParamRef.current = param
//       submitRef.current?.()
//     }
//   })

//   async function onVerify(data: CodeVerifyForm) {
//     try {
//       const result = await authApi.loginByEmail(email, data.code)
//       onSuccess(result.accessToken, result.refreshToken)
//     } catch (err) {
//       // 后端：邮箱不存在 → AUTH_LOGIN_BAD_CREDENTIALS (1000000)；
//       // 与手机验证码登录不同，邮箱端不自动注册，由前端引导跳「邮箱验证码注册」流程
//       if (err instanceof ApiError && err.code === 1000000) {
//         setNotRegistered(true)
//         codeMethods.setError("root", { message: "该邮箱尚未注册" })
//         return
//       }
//       const msg = err instanceof Error ? err.message : "验证失败，请重试"
//       codeMethods.setError("root", { message: msg })
//       notify.error(msg)
//     }
//   }

//   async function onResend() {
//     if (countdown > 0) return
//     try {
//       // 重新发送也走相同的 ESA 流程：用户点按钮触发；
//       // 此处直接调用，沿用同一份 captchaVerifyParam（若已失效后端会拒绝，提示用户回到上一步重新滑动）
//       await sendCode(email, captcha.enabled ? captchaVerifyParamRef.current : undefined)
//       setNotRegistered(false)
//     } catch (err) {
//       notify.error(err instanceof Error ? err.message : "重新发送失败，请重试")
//     } finally {
//       captchaVerifyParamRef.current = ""
//       captcha.reset()
//     }
//   }

//   if (step === "code") {
//     return (
//       <div className="space-y-5">
//         <p className="text-muted-foreground text-sm">
//           验证码已发送至 <span className="font-medium text-foreground">{email}</span>
//         </p>
//         <Form methods={codeMethods} onSubmit={onVerify} className="space-y-5">
//           <FieldOtp name="code" length={6} autoSubmit />

//           <p
//             className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${codeMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
//           >
//             {codeMethods.formState.errors.root?.message ?? " "}
//           </p>

//           {notRegistered ? (
//             <div className="space-y-3 rounded-lg border border-amber-300/60 bg-amber-50 p-4 text-sm dark:border-amber-700/40 dark:bg-amber-950/30">
//               <p className="text-foreground">
//                 邮箱 <span className="font-medium">{email}</span> 尚未注册，是否使用此邮箱立即注册？
//               </p>
//               <Button
//                 type="button"
//                 className="h-10 w-full"
//                 nativeButton={false}
//                 render={<Link href={`${paths.auth.register}?email=${encodeURIComponent(email)}`} />}
//               >
//                 立即注册
//               </Button>
//             </div>
//           ) : (
//             <Button
//               type="submit"
//               className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
//               disabled={codeMethods.formState.isSubmitting}
//             >
//               {codeMethods.formState.isSubmitting ? "验证中..." : "登录"}
//             </Button>
//           )}
//         </Form>

//         <div className="flex items-center justify-between text-sm">
//           <button
//             type="button"
//             className="text-muted-foreground hover:text-primary"
//             onClick={() => {
//               setStep("email")
//               setNotRegistered(false)
//             }}
//           >
//             修改邮箱
//           </button>
//           <button
//             type="button"
//             className={`${countdown > 0 ? "cursor-not-allowed text-muted-foreground" : "text-primary hover:underline"}`}
//             disabled={countdown > 0}
//             onClick={onResend}
//           >
//             {countdown > 0 ? `重新发送 (${countdown}s)` : "重新发送"}
//           </button>
//         </div>
//       </div>
//     )
//   }

//   return (
//     <Form
//       methods={emailMethods}
//       onSubmit={captcha.enabled ? undefined : onSendCode}
//       className="space-y-5"
//     >
//       <FieldText name="email" label="邮箱" type="email" placeholder="your@email.com" />

//       {captcha.enabled && <div id="email-code-captcha" />}

//       <p
//         className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${emailMethods.formState.errors.root ? "opacity-100" : "opacity-0"}`}
//       >
//         {emailMethods.formState.errors.root?.message ?? " "}
//       </p>

//       <Button
//         id="email-code-send-btn"
//         type={captcha.buttonType}
//         className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
//         disabled={emailMethods.formState.isSubmitting}
//       >
//         {emailMethods.formState.isSubmitting ? "发送中..." : "发送验证码"}
//       </Button>
//     </Form>
//   )
// }

// ==================== 手机验证码登录子组件 ====================

function PhoneLoginPanel({
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
      notify.error(msg)
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
      notify.error(msg)
    }
  }

  async function onResend() {
    if (countdown > 0 || !termsAgreed) return
    try {
      await sendCode(phone, captcha.enabled ? captchaVerifyParamRef.current : undefined)
    } catch (err) {
      notify.error(err instanceof Error ? err.message : "重新发送失败，请重试")
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

// ==================== 密码登录子组件 ====================

function PasswordLoginPanel({
  onSuccess
}: {
  onSuccess: (accessToken: string, refreshToken: string, isNewUser?: boolean) => void
}) {
  const captchaVerifyParamRef = useRef<string>("")

  const methods = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" }
  })

  const {
    formState: { isSubmitting, errors }
  } = methods

  async function onSubmit(data: LoginForm) {
    try {
      const result = await authApi.login(
        data.username,
        data.password,
        captcha.enabled ? captchaVerifyParamRef.current : undefined
      )
      onSuccess(result.accessToken, result.refreshToken)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "登录失败，请重试"
      methods.setError("root", { message: msg })
      notify.error(msg)
    } finally {
      captchaVerifyParamRef.current = ""
      captcha.reset()
    }
  }

  const submitRef = useRef<(() => void) | null>(null)
  submitRef.current = methods.handleSubmit(onSubmit)

  const captcha = useEsaCaptcha({
    elementId: "password-login-captcha",
    buttonId: "password-login-btn",
    onVerified: (param) => {
      captchaVerifyParamRef.current = param
      submitRef.current?.()
    }
  })

  return (
    <Form methods={methods} onSubmit={captcha.enabled ? undefined : onSubmit} className="space-y-5">
      <FieldText name="username" label="账号/邮箱" type="text" placeholder="用户名或邮箱" />
      <FieldText name="password" label="密码" type="password" placeholder="输入密码" />

      <div className="mb-0 flex justify-end">
        <Link
          href={paths.auth.forgotPassword}
          className="text-muted-foreground text-sm hover:text-primary"
        >
          忘记密码？
        </Link>
      </div>

      {captcha.enabled && <div id="password-login-captcha" />}

      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {errors.root?.message ?? " "}
      </p>

      <Button
        id="password-login-btn"
        type={captcha.buttonType}
        className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
        disabled={isSubmitting}
      >
        {isSubmitting ? "登录中..." : "登录"}
      </Button>
    </Form>
  )
}

// ==================== 登录页主体 ====================

function LoginContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setTokens, setUser } = useAuthStore()
  const [successUser, setSuccessUser] = useState<string | null>(null)
  const [pendingConsent, setPendingConsent] = useState<LegalDocument[] | null>(null)
  const pendingAuthRef = useRef<{
    accessToken: string
    refreshToken: string
    user: AuthUser
    isNewUser: boolean
  } | null>(null)
  const redirectTo = searchParams.get("redirect") || "/studio/welcome"

  // 处理后端 OAuth 重定向回来的 token 参数
  useEffect(() => {
    const accessToken = searchParams.get("accessToken")
    const refreshToken = searchParams.get("refreshToken")
    if (!accessToken || !refreshToken) return

    setTokens(accessToken, refreshToken)
    authApi.me().then(({ user }) => {
      setUser(user)
      router.replace(redirectTo)
    })
  }, [searchParams, setTokens, setUser, router, redirectTo])

  /**
   * 登录成功处理：
   * 先用临时 axios header 拉取用户信息并暂存，展示 LoginSuccessOverlay 动画；
   * 动画结束时（handleOverlayDone）再写入 store —— 避免 GuestGuard 监听到
   * isAuthenticated 立刻 router.replace，把动画抢跑卸载。
   *
   * 新用户首次通过手机号登录即注册时，isNewUser=true，动画结束后给出欢迎引导。
   *
   * 合规检查：me 之后立即查询 /legal/consent/pending；若有未同意的最新版本，
   * 先弹出 ConsentDialog 强制确认，确认后再继续动画 → 进入工作区。
   */
  async function handleLoginSuccess(
    accessToken: string,
    refreshToken: string,
    isNewUser?: boolean
  ) {
    setAxiosAuth(accessToken)
    const { user } = await authApi.me()
    pendingAuthRef.current = { accessToken, refreshToken, user, isNewUser: !!isNewUser }

    // 检查是否有未同意的法律文档（服务条款 / 隐私政策更新）
    try {
      const pending = await legalApi.pending()
      if (pending.count > 0) {
        setPendingConsent(pending.items)
        return
      }
    } catch {
      // 接口异常不阻塞登录流程，记录到控制台即可
    }

    setSuccessUser(user.nickname || user.username || "")
  }

  /** 用户在 ConsentDialog 中全部同意后回调 */
  const handleConsentConfirmed = useCallback(() => {
    setPendingConsent(null)
    const pending = pendingAuthRef.current
    if (!pending) return
    setSuccessUser(pending.user.nickname || pending.user.username || "")
  }, [])

  /** 用户在 ConsentDialog 中点"不同意，退出登录" */
  const handleConsentDecline = useCallback(() => {
    setPendingConsent(null)
    pendingAuthRef.current = null
    clearAxiosAuth()
    notify.info("您未同意必要条款，已取消本次登录")
  }, [])

  const handleOverlayDone = useCallback(() => {
    const pending = pendingAuthRef.current
    if (!pending) {
      router.push(redirectTo)
      return
    }
    pendingAuthRef.current = null
    setTokens(pending.accessToken, pending.refreshToken)
    setUser(pending.user)
    const dest = redirectTo.startsWith("/studio")
      ? `${redirectTo.split("?")[0]}?welcome=1`
      : redirectTo
    router.push(dest)
    if (pending.isNewUser) {
      notify.success("欢迎！请前往个人中心完善资料", {
        action: {
          label: "去完善",
          onClick: () => router.push(paths.workspace.settingsProfile)
        },
        duration: 8000
      })
    }
  }, [router, redirectTo, setTokens, setUser])

  // async function handleOAuth(provider: string) {
  //   try {
  //     const url = await authApi.getOAuthUrl(provider, "")
  //     window.location.href = url
  //   } catch (err) {
  //     notify.error(err instanceof Error ? err.message : "第三方登录失败，请重试")
  //   }
  // }

  return (
    <div className="w-full max-w-sm space-y-6">
      {pendingConsent && pendingConsent.length > 0 && (
        <ConsentDialog
          items={pendingConsent}
          onAllConfirmed={handleConsentConfirmed}
          onDecline={handleConsentDecline}
        />
      )}
      {successUser !== null ? (
        <LoginSuccessOverlay username={successUser} onDone={handleOverlayDone} />
      ) : (
        <>
          <div>
            <h2 className="font-bold text-2xl">登录</h2>
            <p className="mt-1 text-muted-foreground text-sm">欢迎使用，AI 时代快人一步</p>
          </div>

          <Tabs defaultValue="password">
            <TabsList className="w-full">
              <TabsTrigger value="password" className="flex-1">
                密码登录
              </TabsTrigger>
              <TabsTrigger value="phone" className="flex-1">
                手机验证码
              </TabsTrigger>
            </TabsList>
            <TabsContent value="password" className="pt-4">
              <PasswordLoginPanel onSuccess={handleLoginSuccess} />
            </TabsContent>
            <TabsContent value="phone" className="pt-4">
              <PhoneLoginPanel onSuccess={handleLoginSuccess} />
            </TabsContent>
          </Tabs>

          {/* <Separator /> */}

          {/* 第三方登录（暂时隐藏）
          <div className="space-y-3">
            <p className="text-center text-muted-foreground text-sm">其他登录方式</p>
            <div className="flex justify-center gap-4">
              <Button variant="outline" size="icon" onClick={() => handleOAuth("wechat")}>
                <WechatIcon />
              </Button>
              <Button variant="outline" size="icon" onClick={() => handleOAuth("wecom")}>
                <WecomIcon />
              </Button>
              <Button variant="outline" size="icon" onClick={() => handleOAuth("dingtalk")}>
                <DingtalkIcon />
              </Button>
            </div>
          </div>
          */}

          <p className="text-center text-muted-foreground text-sm">
            还没有账号？{" "}
            <Link href={paths.auth.register} className="text-primary hover:underline">
              立即注册
            </Link>
          </p>
        </>
      )}
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginContent />
    </Suspense>
  )
}

// /* 简化 SVG 图标 */
// function WechatIcon() {
//   return (
//     <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="微信">
//       <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991a.96.96 0 0 1 0 1.92.96.96 0 0 1 0-1.92zm5.812 0a.96.96 0 0 1 0 1.92.96.96 0 0 1 0-1.92zm5.34 2.867c-3.808 0-6.937 2.673-6.937 5.98 0 3.307 3.129 5.98 6.937 5.98.752 0 1.48-.107 2.17-.307a.67.67 0 0 1 .553.074l1.46.854a.252.252 0 0 0 .126.04.227.227 0 0 0 .224-.226c0-.055-.022-.11-.037-.163l-.3-1.133a.456.456 0 0 1 .164-.51C22.886 18.1 24 16.358 24 14.838c0-3.307-3.129-5.98-6.937-5.98h-.126zm-2.634 2.772a.74.74 0 0 1 0 1.48.74.74 0 0 1 0-1.48zm4.2 0a.74.74 0 0 1 0 1.48.74.74 0 0 1 0-1.48z" />
//     </svg>
//   )
// }

// function WecomIcon() {
//   return (
//     <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="企业微信">
//       <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h-2v-6h2v6zm4 0h-2v-6h2v6zm-2-8a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm-4 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z" />
//     </svg>
//   )
// }

// function DingtalkIcon() {
//   return (
//     <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="钉钉">
//       <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 0 0-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z" />
//     </svg>
//   )
// }
