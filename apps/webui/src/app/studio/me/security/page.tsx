/**
 * /studio/me/security——安全设置（修改密码 + 绑定手机号）
 * 从 account 页面拆分为独立路由，参考 (workspace)/settings/security 结构
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { CheckCircle2, Eye, EyeOff, Info, Lock, Phone } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import {
  GlassCard,
  GlassCardBody,
  GlassCardHeader,
  GlassCardTitle,
  GlowButton,
  SectionHaze
} from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  authApi,
  profileApi,
  profileQueries,
  useChangePassword,
  useProfile
} from "@/lib/api/rest/user"
import { notify } from "@/lib/notification"

// 原密码仅做必填校验，不校验长度（后端负责校验正确性）
const passwordSchema = z
  .object({
    oldPassword: z.string().min(1, "请输入当前密码"),
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
type PasswordFormValues = z.infer<typeof passwordSchema>

export default function StudioSecurityPage() {
  return (
    <div className="relative mx-auto max-w-3xl space-y-6 p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-6">
        <h1 className="font-semibold text-xl">安全设置</h1>
        <PasswordSection />
        <BindPhoneSection />
      </div>
    </div>
  )
}

/** 修改密码 */
function PasswordSection() {
  const [showPwd, setShowPwd] = useState(false)
  const changePassword = useChangePassword()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting }
  } = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
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

  return (
    <GlassCard>
      <GlassCardHeader>
        <GlassCardTitle className="flex items-center gap-2">
          <Lock className="size-4" />
          修改密码
        </GlassCardTitle>
      </GlassCardHeader>
      <GlassCardBody>
        <form onSubmit={onSubmit} className="mx-auto max-w-md space-y-5">
          <div className="relative space-y-1.5">
            <Label htmlFor="oldPassword">当前密码</Label>
            <Input
              id="oldPassword"
              type={showPwd ? "text" : "password"}
              aria-invalid={!!errors.oldPassword}
              {...register("oldPassword")}
            />
            <button
              type="button"
              className="absolute top-8 right-3 text-muted-foreground"
              onClick={() => setShowPwd((v) => !v)}
              tabIndex={-1}
            >
              {showPwd ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
            {errors.oldPassword && (
              <p className="text-destructive text-xs">{errors.oldPassword.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="newPassword">新密码</Label>
            <Input
              id="newPassword"
              type={showPwd ? "text" : "password"}
              aria-invalid={!!errors.newPassword}
              {...register("newPassword")}
            />
            {errors.newPassword ? (
              <p className="text-destructive text-xs">{errors.newPassword.message}</p>
            ) : (
              <p className="flex items-center gap-1 text-muted-foreground text-xs">
                <Info className="size-3.5" />
                密码至少 6 位字符
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">确认新密码</Label>
            <Input
              id="confirmPassword"
              type={showPwd ? "text" : "password"}
              aria-invalid={!!errors.confirmPassword}
              {...register("confirmPassword")}
            />
            {errors.confirmPassword && (
              <p className="text-destructive text-xs">{errors.confirmPassword.message}</p>
            )}
          </div>

          <div className="flex justify-end pt-2">
            <GlowButton
              type="submit"
              tone="violet"
              disabled={isSubmitting || changePassword.isPending}
            >
              {isSubmitting ? "修改中..." : "保存修改"}
            </GlowButton>
          </div>
        </form>
      </GlassCardBody>
    </GlassCard>
  )
}

// ==================== 绑定手机号 ====================

const bindPhoneSchema = z.object({
  phone: z
    .string()
    .min(1, "请输入手机号")
    .regex(/^1[3-9]\d{9}$/, "手机号格式不正确"),
  code: z.string().length(6, "验证码为 6 位")
})
type BindPhoneForm = z.infer<typeof bindPhoneSchema>

function BindPhoneSection() {
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
    <GlassCard>
      <GlassCardHeader>
        <GlassCardTitle className="flex items-center gap-2">
          <Phone className="size-4" />
          绑定手机号
        </GlassCardTitle>
      </GlassCardHeader>
      <GlassCardBody>
        {maskedPhone && (
          <p className="mb-4 flex items-center gap-1 text-emerald-500 text-sm">
            <CheckCircle2 className="size-3.5" />
            已绑定 {maskedPhone}，提现时需要手机验证
          </p>
        )}
        {!maskedPhone && (
          <p className="mb-4 text-muted-foreground text-sm">
            绑定手机号后可申请提现，手机号也作为安全验证方式
          </p>
        )}
        <form onSubmit={onSubmit} className="mx-auto max-w-md space-y-4">
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
            <GlowButton type="submit" tone="violet" disabled={isSubmitting}>
              {isSubmitting ? "绑定中..." : "确认绑定"}
            </GlowButton>
          </div>
        </form>
      </GlassCardBody>
    </GlassCard>
  )
}
