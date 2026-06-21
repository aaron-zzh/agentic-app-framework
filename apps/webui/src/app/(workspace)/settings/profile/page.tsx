/**
 * 个人资料页——左栏头像 + 右栏基本信息表单
 * @author AaronZZH & Kiro
 *
 * 设计要点：
 * - 头像走"上传即生效"链路：UploadAvatar 上传成功 → PUT /system/user/profile → refreshUser → header 立即同步
 * - 其他字段（昵称/手机）走"点保存"链路：onSubmit → PUT → refreshUser
 * - 单一刷新点：refreshUser 调 GET /auth/me，后端是 user 数据的唯一真相源
 * - 字段与后端 UserProfileVO/UserProfileUpdateDTO 严格对齐：仅 nickname/avatar/email/phone
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { UploadAvatar } from "@/components/upload"
import { useProfile, useUpdateProfile } from "@/lib/api/rest/user/profile"
import { useAuth } from "@/lib/auth/use-auth"
import { notify } from "@/lib/notification"

// ─── Schemas ─────────────────────────────────────────────────────────────────

/** 字段与后端 UserProfileUpdateDTO 严格对齐，避免前端写入"假字段"后端默默丢弃 */
const profileSchema = z.object({
  nickname: z.string().min(1, "昵称不能为空"),
  phone: z.string().optional(),
  avatar: z.string().optional()
})

type ProfileFormValues = z.infer<typeof profileSchema>

// ─── 主页面 ───────────────────────────────────────────────────────────────────

export default function ProfilePage() {
  const { data: profile, isLoading } = useProfile()

  if (isLoading) {
    return (
      <div className="p-6">
        <div className="grid grid-cols-1 gap-6 md:grid-cols-[280px_1fr]">
          <Skeleton className="h-80 rounded-xl" />
          <Skeleton className="h-80 rounded-xl" />
        </div>
      </div>
    )
  }

  return (
    <div className="p-6">
      <ProfileForm profile={profile} />
    </div>
  )
}

// ─── 双列表单 ─────────────────────────────────────────────────────────────────

function ProfileForm({ profile }: { profile: ReturnType<typeof useProfile>["data"] }) {
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

  /** 提交基本信息——后端写库后调 refreshUser 让 store 同步最新字段 */
  const onSubmit = async (data: ProfileFormValues) => {
    try {
      await updateProfile.mutateAsync(data)
      await refreshUser()
      notify.success("个人资料已更新")
    } catch {
      notify.error("更新失败，请重试")
    }
  }

  /** 头像上传成功立即生效——PUT 后端 → refreshUser 同步 store → header 自动更新 */
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

  return (
    <Form methods={methods} onSubmit={onSubmit}>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[280px_1fr]">
        {/* ── 左栏：头像卡 ── */}
        <Card className="flex flex-col items-center gap-4 p-6 text-center">
          <UploadAvatar
            value={avatar}
            onChange={handleAvatarChange}
            imageOptions={{ maxWidth: 512, maxHeight: 512, quality: 0.85 }}
          />

          <p className="text-muted-foreground text-xs">
            点击上传头像
            <br />
            支持 *.jpeg · *.jpg · *.png · *.webp ｜ 最大 3MB
          </p>
        </Card>

        {/* ── 右栏：表单网格 ── */}
        <Card>
          <CardHeader>
            <CardTitle>基本信息</CardTitle>
            <CardDescription>管理你的个人信息（与后端字段一致）</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field.Text name="nickname" label="昵称" placeholder="你的昵称" />

              <div className="space-y-1.5">
                <Label>邮箱</Label>
                <Input value={profile?.email ?? ""} disabled className="bg-muted" />
              </div>

              <Field.Text name="phone" label="手机号" placeholder="选填" />
            </div>

            <div className="flex justify-end">
              <Button type="submit" disabled={updateProfile.isPending}>
                {updateProfile.isPending ? "保存中..." : "保存修改"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </Form>
  )
}
