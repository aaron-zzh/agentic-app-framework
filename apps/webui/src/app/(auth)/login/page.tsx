/**
 * 登录页——账号/邮箱密码登录 + 第三方 OAuth 入口
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
import { notify } from "@/lib/notification"
import { useAuthStore } from "@/lib/store/auth-store"

const CAPTCHA_ENABLED = process.env.NEXT_PUBLIC_CAPTCHA_ENABLED === "true"
const CAPTCHA_SCENE_ID = process.env.NEXT_PUBLIC_CAPTCHA_SCENE_ID ?? ""

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

/** 登录页主体，需包在 Suspense 内（useSearchParams 要求） */
function LoginContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setTokens, setUser } = useAuthStore()
  const captchaVerifyParamRef = useRef<string>("")

  // 处理后端 OAuth 重定向回来的 token 参数
  useEffect(() => {
    const accessToken = searchParams.get("accessToken")
    const refreshToken = searchParams.get("refreshToken")
    if (!accessToken || !refreshToken) return

    setTokens(accessToken, refreshToken)
    authApi.me().then(({ user }) => {
      setUser(user)
      router.replace(paths.workspace.root)
    })
  }, [searchParams, setTokens, setUser, router])

  // 初始化阿里云 ESA AI 验证码
  useEffect(() => {
    if (!CAPTCHA_ENABLED || typeof window === "undefined") return
    if (!window.initAliyunCaptcha) return

    window.initAliyunCaptcha({
      SceneId: CAPTCHA_SCENE_ID,
      mode: "popup",
      element: "#captcha-element",
      button: "#login-btn",
      success: (captchaVerifyParam: string) => {
        captchaVerifyParamRef.current = captchaVerifyParam
      },
      fail: (_result: unknown) => {
        // console.error("验证码验证失败", result)
      },
      getInstance: (instance: unknown) => {
        window.__aliyunCaptchaInstance = instance
      },
      server: ["captcha-esa-open.aliyuncs.com", "captcha-esa-open-b.aliyuncs.com"],
      slideStyle: { width: 360, height: 40 }
    })
  }, [])

  const methods = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" }
  })

  const {
    formState: { isSubmitting, errors }
  } = methods

  async function onSubmit(data: LoginForm) {
    try {
      // 验证码启用时，captchaVerifyParam 由 ESA 边缘节点验签（附在请求头中）
      // 验证码加载失败时降级跳过，不阻塞登录
      const captchaFailed = typeof window !== "undefined" && window.__captchaLoadFailed
      const result = await authApi.login(
        data.username,
        data.password,
        CAPTCHA_ENABLED && !captchaFailed ? captchaVerifyParamRef.current : undefined
      )
      setTokens(result.accessToken, result.refreshToken)
      const { user } = await authApi.me()
      setUser(user)
      router.push(paths.workspace.root)
    } catch (err) {
      // 表单内展示（可修正的输入错误）+ toast 兜底（网络超时等也能感知）
      const msg = err instanceof Error ? err.message : "登录失败，请重试"
      methods.setError("root", { message: msg })
      notify.error(msg)
    }
  }

  async function handleOAuth(provider: string) {
    try {
      const url = await authApi.getOAuthUrl(provider, "")
      window.location.href = url
    } catch (err) {
      notify.error(err instanceof Error ? err.message : "第三方登录失败，请重试")
    }
  }

  return (
    <div className="w-full max-w-sm space-y-6">
      <div>
        <h2 className="font-bold text-2xl">登录</h2>
        <p className="mt-1 text-muted-foreground text-sm">输入账号或邮箱登录系统</p>
      </div>

      <Form methods={methods} onSubmit={onSubmit} className="space-y-5">
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

        {/* 验证码挂载点（弹出式，仅启用时渲染） */}
        {CAPTCHA_ENABLED && <div id="captcha-element" />}

        {/* 接口级错误——始终占位避免抖动，有错时淡入 */}
        <p
          className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${errors.root ? "opacity-100" : "opacity-0"}`}
        >
          {errors.root?.message ?? " "}
        </p>

        <Button id="login-btn" type="submit" className="h-11 w-full" disabled={isSubmitting}>
          {isSubmitting ? "登录中..." : "登录"}
        </Button>
      </Form>

      <Separator />

      {/* 第三方登录 */}
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

      <p className="text-center text-muted-foreground text-sm">
        还没有账号？{" "}
        <Link href={paths.auth.register} className="text-primary hover:underline">
          立即注册
        </Link>
      </p>
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

/* 简化 SVG 图标 */
function WechatIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="微信">
      <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991a.96.96 0 0 1 0 1.92.96.96 0 0 1 0-1.92zm5.812 0a.96.96 0 0 1 0 1.92.96.96 0 0 1 0-1.92zm5.34 2.867c-3.808 0-6.937 2.673-6.937 5.98 0 3.307 3.129 5.98 6.937 5.98.752 0 1.48-.107 2.17-.307a.67.67 0 0 1 .553.074l1.46.854a.252.252 0 0 0 .126.04.227.227 0 0 0 .224-.226c0-.055-.022-.11-.037-.163l-.3-1.133a.456.456 0 0 1 .164-.51C22.886 18.1 24 16.358 24 14.838c0-3.307-3.129-5.98-6.937-5.98h-.126zm-2.634 2.772a.74.74 0 0 1 0 1.48.74.74 0 0 1 0-1.48zm4.2 0a.74.74 0 0 1 0 1.48.74.74 0 0 1 0-1.48z" />
    </svg>
  )
}

function WecomIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="企业微信">
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h-2v-6h2v6zm4 0h-2v-6h2v6zm-2-8a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm-4 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z" />
    </svg>
  )
}

function DingtalkIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-label="钉钉">
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 0 0-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z" />
    </svg>
  )
}
