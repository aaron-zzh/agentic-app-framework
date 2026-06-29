"use client"

/**
 * 密码登录面板——账号/邮箱 + 密码
 * @author AaronZZH & Kiro
 */

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRef } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"

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

export function PasswordLoginPanel({
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
