"use client"

/**
 * 密码登录面板——账号/邮箱 + 密码
 * 不含提交按钮和 captcha，由父层 LoginContent 统一持有。
 * 通过 ref.triggerSubmit(captchaParam?) 触发提交。
 * @author AaronZZH & Kiro
 */

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { type RefObject, useImperativeHandle } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { FieldText } from "@/components/form/field-text"
import { Form } from "@/components/form/form"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
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

export interface PasswordLoginPanelRef {
  /** 父层捕获 captcha 验证结果后调用，触发实际登录请求 */
  triggerSubmit: (captchaParam?: string) => Promise<void>
  /** 提交前校验表单，返回是否通过 */
  validate: () => Promise<boolean>
}

export function PasswordLoginPanel({
  onSuccess,
  onError,
  ref
}: {
  onSuccess: (accessToken: string, refreshToken: string, isNewUser?: boolean) => void
  onError?: () => void
  ref?: RefObject<PasswordLoginPanelRef | null>
}) {
  const methods = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" }
  })

  const {
    formState: { errors }
  } = methods

  async function doSubmit(data: LoginForm, captchaParam?: string) {
    try {
      const result = await authApi.login(data.username, data.password, captchaParam)
      onSuccess(result.accessToken, result.refreshToken)
    } catch (err) {
      const msg = err instanceof Error ? err.message : "登录失败，请重试"
      methods.setError("root", { message: msg })
      notify.error(msg)
      onError?.()
    }
  }

  useImperativeHandle(ref, () => ({
    triggerSubmit: async (captchaParam?: string) => {
      await methods.handleSubmit((data) => doSubmit(data, captchaParam))()
    },
    validate: () => methods.trigger()
  }))

  return (
    <Form methods={methods} onSubmit={undefined} className="space-y-5">
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

      <p
        className={`mb-1 min-h-[1.25rem] text-destructive text-sm transition-opacity duration-200 ${errors.root ? "opacity-100" : "opacity-0"}`}
      >
        {errors.root?.message ?? " "}
      </p>
    </Form>
  )
}
