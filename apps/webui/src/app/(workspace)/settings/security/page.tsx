/**
 * 安全设置——修改密码 + 绑定手机号
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { CheckCircle2, Eye, EyeOff, Info, Phone } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { authApi } from "@/lib/api/rest/user/auth"
import {
  profileApi,
  profileQueries,
  useChangePassword,
  useProfile
} from "@/lib/api/rest/user/profile"
import { notify } from "@/lib/notification"

const schema = z
  .object({
    oldPassword: z.string().min(1, "请输入当前密码").min(6, "密码至少 6 位"),
    newPassword: z.string().min(6, "新密码至少 6 位"),
    confirmPassword: z.string().min(1, "请确认新密码")
  })
  .refine((v) => v.oldPassword !== v.newPassword, {
    message: "新密码不能与当前密码相同",
    path: ["newPassword"]
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "两次输入的密码不一致",
    path: ["confirmPassword"]
  })

type FormValues = z.infer<typeof schema>

function PwdField({
  id,
  label,
  show,
  error,
  helperText,
  registration
}: {
  id: string
  label: string
  show: boolean
  error?: string
  helperText?: React.ReactNode
  registration: ReturnType<ReturnType<typeof useForm<FormValues>>["register"]>
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={show ? "text" : "password"} aria-invalid={!!error} {...registration} />
      {helperText && !error && (
        <p className="flex items-center gap-1 text-muted-foreground text-xs">{helperText}</p>
      )}
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

export default function SecurityPage() {
  const [showPwd, setShowPwd] = useState(false)
  const changePassword = useChangePassword()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { oldPassword: "", newPassword: "", confirmPassword: "" }
  })

  const onSubmit = handleSubmit(async (data) => {
    try {
      await changePassword.mutateAsync({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword
      })
      reset()
      notify.success("密码修改成功")
    } catch {
      notify.error("密码修改失败，请检查当前密码是否正确")
    }
  })

  const ToggleBtn = () => (
    <button
      type="button"
      className="absolute top-1/2 right-3 -translate-y-1/2 text-muted-foreground"
      onClick={() => setShowPwd((v) => !v)}
      tabIndex={-1}
    >
      {showPwd ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
    </button>
  )

  return (
    <div className="p-6">
      <form onSubmit={onSubmit}>
        <Card className="max-w-lg">
          <CardHeader>
            <CardTitle>修改密码</CardTitle>
            <CardDescription>定期修改密码保障账户安全</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="relative">
              <PwdField
                id="oldPassword"
                label="当前密码"
                show={showPwd}
                error={errors.oldPassword?.message}
                registration={register("oldPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="relative">
              <PwdField
                id="newPassword"
                label="新密码"
                show={showPwd}
                error={errors.newPassword?.message}
                helperText={
                  <>
                    <Info className="size-3.5" />
                    密码至少 6 位字符
                  </>
                }
                registration={register("newPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="relative">
              <PwdField
                id="confirmPassword"
                label="确认新密码"
                show={showPwd}
                error={errors.confirmPassword?.message}
                registration={register("confirmPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isSubmitting || changePassword.isPending}>
                {isSubmitting ? "修改中..." : "保存修改"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </form>
      <BindPhoneCard />
    </div>
  )
}

// ==================== 绑定手机号卡片 ====================

const bindPhoneSchema = z.object({
  phone: z
    .string()
    .min(1, "请输入手机号")
    .regex(/^1[3-9]\d{9}$/, "手机号格式不正确"),
  code: z.string().length(6, "验证码为 6 位")
})

type BindPhoneForm = z.infer<typeof bindPhoneSchema>

function BindPhoneCard() {
  const { data: profile } = useProfile()
  const qc = useQueryClient()
  const [countdown, setCountdown] = useState(0)
  const [sending, setSending] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting }
  } = useForm<BindPhoneForm>({
    resolver: zodResolver(bindPhoneSchema),
    defaultValues: { phone: "", code: "" }
  })

  const phone = watch("phone")
  const phoneValid = /^1[3-9]\d{9}$/.test(phone)

  async function sendCode() {
    if (!phoneValid || countdown > 0) return
    setSending(true)
    try {
      await authApi.sendSmsCode(phone, "bind")
      notify.success("验证码已发送")
      setCountdown(60)
      const timer = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) {
            clearInterval(timer)
            return 0
          }
          return c - 1
        })
      }, 1000)
    } catch (e) {
      notify.error(e instanceof Error ? e.message : "发送失败，请稍后重试")
    } finally {
      setSending(false)
    }
  }

  const onSubmit = handleSubmit(async (data) => {
    try {
      await profileApi.bindPhone(data.phone, data.code)
      qc.invalidateQueries({ queryKey: profileQueries.detail().queryKey })
      notify.success("手机号绑定成功")
    } catch (e) {
      notify.error(e instanceof Error ? e.message : "绑定失败，请检查验证码")
    }
  })

  const maskedPhone = profile?.phone
    ? profile.phone.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2")
    : null

  return (
    <Card className="mt-6 max-w-lg">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Phone className="size-4" />
          绑定手机号
        </CardTitle>
        <CardDescription>
          {maskedPhone ? (
            <span className="flex items-center gap-1 text-emerald-600">
              <CheckCircle2 className="size-3.5" />
              已绑定 {maskedPhone}，提现时需要手机验证
            </span>
          ) : (
            "绑定手机号后可申请提现，手机号也作为安全验证方式"
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="bind-phone">手机号</Label>
            <Input
              id="bind-phone"
              placeholder="请输入新手机号"
              {...register("phone")}
              aria-invalid={!!errors.phone}
            />
            {errors.phone && <p className="text-destructive text-xs">{errors.phone.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="bind-code">验证码</Label>
            <div className="flex gap-2">
              <Input
                id="bind-code"
                placeholder="6 位验证码"
                maxLength={6}
                {...register("code")}
                aria-invalid={!!errors.code}
              />
              <Button
                type="button"
                variant="outline"
                disabled={!phoneValid || countdown > 0 || sending}
                onClick={sendCode}
                className="shrink-0"
              >
                {countdown > 0 ? `${countdown}s` : sending ? "发送中..." : "发送验证码"}
              </Button>
            </div>
            {errors.code && <p className="text-destructive text-xs">{errors.code.message}</p>}
          </div>
          <div className="flex justify-end pt-1">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "绑定中..." : "确认绑定"}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
