/**
 * /studio/me/account——账号设置（迁移自 workspace/settings/profile）
 * 不跳出 Studio 外壳，复用业务逻辑 + Studio 风格层
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import {
  GlassCard,
  GlassCardBody,
  GlassCardHeader,
  GlassCardTitle,
  GlowButton,
  SectionHaze
} from "@/components/studio"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { UploadAvatar } from "@/components/upload"
import { useProfile, useUpdateProfile } from "@/lib/api/rest/user/profile"
import { useAuth } from "@/lib/auth/use-auth"
import { notify } from "@/lib/notification"

const profileSchema = z.object({
  nickname: z.string().min(1, "昵称不能为空"),
  phone: z.string().optional(),
  avatar: z.string().optional()
})
type ProfileFormValues = z.infer<typeof profileSchema>

export default function StudioMeAccountPage() {
  const { data: profile, isLoading } = useProfile()
  const updateProfile = useUpdateProfile()
  const { refreshUser } = useAuth()

  const methods = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { nickname: "", phone: "", avatar: "" }
  })

  useEffect(() => {
    if (profile) {
      methods.reset({
        nickname: profile.nickname ?? "",
        phone: profile.phone ?? "",
        avatar: profile.avatar ?? ""
      })
    }
  }, [profile, methods])

  const onSubmit = async (data: ProfileFormValues) => {
    try {
      await updateProfile.mutateAsync(data)
      await refreshUser()
      notify.success("个人资料已更新")
    } catch {
      notify.error("更新失败，请重试")
    }
  }

  const handleAvatarChange = async (url: string) => {
    methods.setValue("avatar", url, { shouldValidate: true })
    try {
      await updateProfile.mutateAsync({ avatar: url })
      await refreshUser()
      notify.success("头像已更新")
    } catch {
      notify.error("头像更新失败")
    }
  }

  const avatar = methods.watch("avatar")

  if (isLoading) {
    return (
      <div className="relative mx-auto max-w-3xl space-y-4 p-6">
        <Skeleton className="h-64 rounded-2xl" />
        <Skeleton className="h-48 rounded-2xl" />
      </div>
    )
  }

  return (
    <div className="relative mx-auto max-w-3xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-6">
        <h1 className="font-semibold text-xl">账号设置</h1>

        <Form methods={methods} onSubmit={onSubmit}>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-[220px_1fr]">
            {/* 头像卡 */}
            <GlassCard>
              <GlassCardBody className="flex flex-col items-center gap-4 text-center">
                <UploadAvatar
                  value={avatar}
                  onChange={handleAvatarChange}
                  imageOptions={{ maxWidth: 512, maxHeight: 512, quality: 0.85 }}
                />
                <p className="text-muted-foreground text-xs">
                  点击上传头像
                  <br />
                  *.jpeg · *.png · *.webp ｜ 最大 3MB
                </p>
              </GlassCardBody>
            </GlassCard>

            {/* 基本信息 */}
            <GlassCard>
              <GlassCardHeader>
                <GlassCardTitle>基本信息</GlassCardTitle>
              </GlassCardHeader>
              <GlassCardBody className="space-y-5">
                <Field.Text name="nickname" label="昵称" placeholder="你的昵称" />

                <div className="space-y-1.5">
                  <Label>邮箱</Label>
                  <Input value={profile?.email ?? ""} disabled className="bg-muted/40" />
                </div>

                <Field.Text name="phone" label="手机号" disabled className="bg-muted/40" />

                <div className="flex justify-end pt-2">
                  <GlowButton type="submit" tone="violet" disabled={updateProfile.isPending}>
                    {updateProfile.isPending ? "保存中..." : "保存修改"}
                  </GlowButton>
                </div>
              </GlassCardBody>
            </GlassCard>
          </div>
        </Form>
      </div>
    </div>
  )
}
