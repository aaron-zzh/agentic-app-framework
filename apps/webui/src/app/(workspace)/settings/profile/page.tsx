/**
 * 个人资料页——左栏头像卡（上传/公开/删除）+ 右栏表单网格
 * 参考 minimal-ui AccountGeneral 双列布局
 * @author AaronZZH
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { useProfile, useUpdateProfile } from "@/lib/api/rest/user/profile"
import { notify } from "@/lib/notification"
import { useAuthStore } from "@/lib/store/auth-store"

// ─── Schemas ─────────────────────────────────────────────────────────────────

const profileSchema = z.object({
  nickname: z.string().min(1, "昵称不能为空"),
  phone: z.string().optional(),
  bio: z.string().optional(),
  avatar: z.string().optional(),
  isPublic: z.boolean().optional(),
  country: z.string().optional(),
  state: z.string().optional(),
  city: z.string().optional(),
  address: z.string().optional(),
  zipCode: z.string().optional()
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
  const setUser = useAuthStore((s) => s.setUser)

  const methods = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      nickname: "",
      phone: "",
      bio: "",
      avatar: "",
      isPublic: false,
      country: "",
      state: "",
      city: "",
      address: "",
      zipCode: ""
    }
  })

  useEffect(() => {
    if (profile) {
      methods.reset({
        nickname: profile.nickname ?? "",
        phone: profile.phone ?? "",
        bio: profile.bio ?? "",
        avatar: profile.avatar ?? "",
        isPublic: false,
        country: "",
        state: "",
        city: "",
        address: "",
        zipCode: ""
      })
    }
  }, [profile, methods])

  const onSubmit = async (data: ProfileFormValues) => {
    try {
      const updated = await updateProfile.mutateAsync(data)
      setUser({
        id: updated.id,
        username: "",
        email: updated.email,
        nickname: updated.nickname,
        avatar: updated.avatar
      })
      notify.success("个人资料已更新")
    } catch {
      notify.error("更新失败，请重试")
    }
  }

  const avatar = methods.watch("avatar")
  const isPublic = methods.watch("isPublic")

  return (
    <Form methods={methods} onSubmit={onSubmit}>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[280px_1fr]">
        {/* ── 左栏：头像卡 ── */}
        <Card className="flex flex-col items-center gap-4 p-6 text-center">
          <div className="relative">
            <Avatar className="size-24 text-3xl">
              <AvatarImage src={avatar} alt={profile?.nickname} />
              <AvatarFallback>{profile?.nickname?.charAt(0) ?? "U"}</AvatarFallback>
            </Avatar>
          </div>

          <Field.Upload name="avatar" accept="image/*" placeholder="上传头像（≤3MB）" />

          <p className="text-muted-foreground text-xs">
            支持 *.jpeg · *.jpg · *.png
            <br />
            最大 3MB
          </p>

          <Separator className="w-full" />

          {/* 公开资料 toggle */}
          <div className="flex w-full items-center justify-between">
            <span className="text-sm">公开资料</span>
            <Switch
              checked={isPublic ?? false}
              onCheckedChange={(v) => methods.setValue("isPublic", v)}
              aria-label="公开资料"
            />
          </div>

          <Separator className="w-full" />

          {/* 删除账户 */}
          <Button
            type="button"
            variant="destructive"
            size="sm"
            className="w-full"
            onClick={() => notify.error("请联系管理员删除账户")}
          >
            删除账户
          </Button>
        </Card>

        {/* ── 右栏：表单网格 ── */}
        <Card>
          <CardHeader>
            <CardTitle>基本信息</CardTitle>
            <CardDescription>管理你的个人信息</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field.Text name="nickname" label="昵称" placeholder="你的昵称" />

              <div className="space-y-1.5">
                <Label>邮箱</Label>
                <Input value={profile?.email ?? ""} disabled className="bg-muted" />
              </div>

              <Field.Text name="phone" label="手机号" placeholder="选填" />
              <Field.Text name="address" label="地址" placeholder="详细地址" />
              <Field.Text name="country" label="国家/地区" placeholder="选填" />
              <Field.Text name="state" label="省/州" placeholder="选填" />
              <Field.Text name="city" label="城市" placeholder="选填" />
              <Field.Text name="zipCode" label="邮政编码" placeholder="选填" />
            </div>

            <Field.Textarea name="bio" label="个人简介" placeholder="介绍一下自己..." />

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
