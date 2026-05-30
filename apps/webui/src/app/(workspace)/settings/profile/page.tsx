/**
 * 个人中心页面——用户概览 + 最近活动卡片 + 资料编辑 + 修改密码
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import {
  Bot,
  ChevronRight,
  Eye,
  EyeOff,
  Image as ImageIcon,
  ListTodo,
  MessageSquare
} from "lucide-react"
import Link from "next/link"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Form } from "@/components/form/form"
import { Field } from "@/components/form/fields"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { notify } from "@/lib/notification"
import { useChatSessions } from "@/lib/queries/use-chat"
import { useMediaAssets } from "@/lib/queries/use-media-assets"
import { useChangePassword, useProfile, useUpdateProfile } from "@/lib/queries/use-profile"
import { useTodos } from "@/lib/queries/use-todos"
import { useAuthStore } from "@/lib/store/auth-store"

// ─── 表单 Schema ────────────────────────────────────────────────────────────

const profileSchema = z.object({
  nickname: z.string().min(1, "昵称不能为空"),
  phone: z.string().optional(),
  bio: z.string().optional(),
  avatar: z.string().optional()
})

type ProfileFormValues = z.infer<typeof profileSchema>

const passwordSchema = z
  .object({
    oldPassword: z.string().min(1, "请输入当前密码"),
    newPassword: z.string().min(6, "新密码至少 6 位"),
    confirmPassword: z.string().min(1, "请确认新密码")
  })
  .refine((v) => v.newPassword !== v.oldPassword, {
    message: "新密码不能与当前密码相同",
    path: ["newPassword"]
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "两次输入的密码不一致",
    path: ["confirmPassword"]
  })

type PasswordFormValues = z.infer<typeof passwordSchema>

// ─── 页面组件 ───────────────────────────────────────────────────────────────

export default function ProfilePage() {
  const { data: profile, isLoading } = useProfile()

  if (isLoading) {
    return <ProfileSkeleton />
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-6">
      {/* 用户头部 */}
      <UserHeader profile={profile} />

      {/* 最近活动卡片 */}
      <ActivityCards />

      <Separator />

      {/* 资料编辑 */}
      <ProfileFormSection profile={profile} />

      <Separator />

      {/* 修改密码 */}
      <ChangePasswordSection />
    </div>
  )
}

// ─── 用户头部 ───────────────────────────────────────────────────────────────

function UserHeader({ profile }: { profile: ReturnType<typeof useProfile>["data"] }) {
  return (
    <div className="flex items-center gap-4">
      <Avatar className="size-16">
        <AvatarImage src={profile?.avatar} alt={profile?.nickname} />
        <AvatarFallback className="text-xl">{profile?.nickname?.charAt(0) ?? "U"}</AvatarFallback>
      </Avatar>
      <div>
        <h1 className="font-semibold text-xl">{profile?.nickname ?? "用户"}</h1>
        <p className="text-muted-foreground text-sm">{profile?.email}</p>
        {profile?.bio && <p className="mt-1 text-muted-foreground text-sm">{profile.bio}</p>}
      </div>
    </div>
  )
}

// ─── 最近活动卡片区域 ───────────────────────────────────────────────────────

function ActivityCards() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <RecentChatsCard />
      <RecentTodosCard />
      <MyAgentsCard />
      <MyAssetsCard />
    </div>
  )
}

/** 最近聊天 */
function RecentChatsCard() {
  const { data, isLoading } = useChatSessions()
  const sessions = data?.list?.slice(0, 3) ?? []

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-2">
        <div className="flex items-center gap-2">
          <MessageSquare className="size-4 text-primary" />
          <CardTitle className="text-sm">最近聊天</CardTitle>
        </div>
        <Link href="/ai/agents" className="flex items-center text-muted-foreground text-xs hover:text-primary">
          查看更多 <ChevronRight className="size-3" />
        </Link>
      </CardHeader>
      <CardContent className="pt-0">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        ) : sessions.length === 0 ? (
          <p className="text-muted-foreground text-xs">暂无聊天记录</p>
        ) : (
          <ul className="space-y-1.5">
            {sessions.map((s) => (
              <li key={s.id} className="flex items-center justify-between text-sm">
                <span className="truncate">{s.title || "未命名对话"}</span>
                <span className="shrink-0 text-muted-foreground text-xs">
                  {new Date(s.updatedAt).toLocaleDateString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

/** 待办任务 */
function RecentTodosCard() {
  const { data, isLoading } = useTodos({ pageSize: 3, status: "pending" })
  const todos = data?.list ?? []

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-2">
        <div className="flex items-center gap-2">
          <ListTodo className="size-4 text-primary" />
          <CardTitle className="text-sm">待办任务</CardTitle>
        </div>
        <Link href="/todos" className="flex items-center text-muted-foreground text-xs hover:text-primary">
          查看更多 <ChevronRight className="size-3" />
        </Link>
      </CardHeader>
      <CardContent className="pt-0">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        ) : todos.length === 0 ? (
          <p className="text-muted-foreground text-xs">暂无待办</p>
        ) : (
          <ul className="space-y-1.5">
            {todos.map((t) => (
              <li key={t.id} className="flex items-center justify-between text-sm">
                <span className="truncate">{t.title}</span>
                {t.dueDate && (
                  <span className="shrink-0 text-muted-foreground text-xs">
                    {new Date(t.dueDate).toLocaleDateString()}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

/** 我的助理 */
function MyAgentsCard() {
  const { data, isLoading } = useChatSessions()
  // 从会话中提取有 agentId 的去重助理
  const agents = Array.from(
    new Map(
      (data?.list ?? []).filter((s) => s.agentId).map((s) => [s.agentId, s])
    ).values()
  ).slice(0, 3)

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-2">
        <div className="flex items-center gap-2">
          <Bot className="size-4 text-primary" />
          <CardTitle className="text-sm">我的助理</CardTitle>
        </div>
        <Link href="/ai/agents" className="flex items-center text-muted-foreground text-xs hover:text-primary">
          查看更多 <ChevronRight className="size-3" />
        </Link>
      </CardHeader>
      <CardContent className="pt-0">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        ) : agents.length === 0 ? (
          <p className="text-muted-foreground text-xs">暂无助理</p>
        ) : (
          <ul className="space-y-1.5">
            {agents.map((a) => (
              <li key={a.agentId} className="flex items-center gap-2 text-sm">
                <Bot className="size-3.5 text-muted-foreground" />
                <span className="truncate">{a.title || a.agentId}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

/** 素材库 */
function MyAssetsCard() {
  const { data, isLoading } = useMediaAssets({ pageSize: 4 })
  const assets = data?.list ?? []

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-2">
        <div className="flex items-center gap-2">
          <ImageIcon className="size-4 text-primary" />
          <CardTitle className="text-sm">素材库</CardTitle>
        </div>
        <Link href="/aigc/assets" className="flex items-center text-muted-foreground text-xs hover:text-primary">
          查看更多 <ChevronRight className="size-3" />
        </Link>
      </CardHeader>
      <CardContent className="pt-0">
        {isLoading ? (
          <div className="flex gap-2">
            <Skeleton className="size-12 rounded" />
            <Skeleton className="size-12 rounded" />
            <Skeleton className="size-12 rounded" />
          </div>
        ) : assets.length === 0 ? (
          <p className="text-muted-foreground text-xs">暂无素材</p>
        ) : (
          <div className="flex gap-2 overflow-hidden">
            {assets.map((a) => (
              <div key={a.id} className="size-12 shrink-0 overflow-hidden rounded border">
                {/* biome-ignore lint/performance/noImgElement: 远程素材缩略图 */}
                <img
                  src={a.thumbnailUrl ?? a.url}
                  alt={a.name}
                  className="size-full object-cover"
                />
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ─── 基本信息编辑 ───────────────────────────────────────────────────────────

function ProfileFormSection({ profile }: { profile: ReturnType<typeof useProfile>["data"] }) {
  const updateProfile = useUpdateProfile()
  const setUser = useAuthStore((s) => s.setUser)

  const methods = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      nickname: profile?.nickname ?? "",
      phone: profile?.phone ?? "",
      bio: profile?.bio ?? "",
      avatar: profile?.avatar ?? ""
    }
  })

  useEffect(() => {
    if (profile) {
      methods.reset({
        nickname: profile.nickname,
        phone: profile.phone ?? "",
        bio: profile.bio ?? "",
        avatar: profile.avatar ?? ""
      })
    }
  }, [profile, methods])

  const onSubmit = async (data: ProfileFormValues) => {
    try {
      const updated = await updateProfile.mutateAsync(data)
      setUser({ id: updated.id, email: updated.email, nickname: updated.nickname, avatar: updated.avatar })
      notify.success("个人资料已更新")
    } catch {
      notify.error("更新失败，请重试")
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>编辑资料</CardTitle>
        <CardDescription>管理你的个人信息和头像</CardDescription>
      </CardHeader>
      <CardContent>
        <Form methods={methods} onSubmit={onSubmit} className="space-y-6">
          <div className="flex items-center gap-4">
            <Avatar className="size-20">
              <AvatarImage src={methods.watch("avatar")} alt="头像" />
              <AvatarFallback className="text-lg">
                {profile?.nickname?.charAt(0) ?? "U"}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1">
              <Field.Upload name="avatar" accept="image/*" placeholder="点击或拖拽上传头像" />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>邮箱</Label>
            <Input value={profile?.email ?? ""} disabled className="bg-muted" />
            <p className="text-muted-foreground text-xs">邮箱不可修改</p>
          </div>

          <Field.Text name="nickname" label="昵称" placeholder="输入你的昵称" />
          <Field.Text name="phone" label="手机号" placeholder="输入手机号（选填）" />
          <Field.Textarea name="bio" label="个人简介" placeholder="介绍一下自己..." />

          <div className="flex justify-end">
            <Button type="submit" disabled={updateProfile.isPending}>
              {updateProfile.isPending ? "保存中..." : "保存修改"}
            </Button>
          </div>
        </Form>
      </CardContent>
    </Card>
  )
}

// ─── 修改密码 ───────────────────────────────────────────────────────────────

function ChangePasswordSection() {
  const changePassword = useChangePassword()
  const [showPassword, setShowPassword] = useState(false)

  const methods = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { oldPassword: "", newPassword: "", confirmPassword: "" }
  })

  const onSubmit = async (data: PasswordFormValues) => {
    try {
      await changePassword.mutateAsync({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword
      })
      methods.reset()
      notify.success("密码修改成功")
    } catch {
      notify.error("密码修改失败，请检查当前密码是否正确")
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>修改密码</CardTitle>
        <CardDescription>定期修改密码以保障账户安全</CardDescription>
      </CardHeader>
      <CardContent>
        <Form methods={methods} onSubmit={onSubmit} className="space-y-4">
          <Field.Text
            name="oldPassword"
            label="当前密码"
            type={showPassword ? "text" : "password"}
            placeholder="输入当前密码"
          />
          <Field.Text
            name="newPassword"
            label="新密码"
            type={showPassword ? "text" : "password"}
            placeholder="至少 6 位"
          />
          <Field.Text
            name="confirmPassword"
            label="确认新密码"
            type={showPassword ? "text" : "password"}
            placeholder="再次输入新密码"
          />

          <div className="flex items-center justify-between">
            <button
              type="button"
              className="flex items-center gap-1 text-muted-foreground text-xs hover:text-foreground"
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
              {showPassword ? "隐藏密码" : "显示密码"}
            </button>
            <Button type="submit" disabled={changePassword.isPending}>
              {changePassword.isPending ? "修改中..." : "修改密码"}
            </Button>
          </div>
        </Form>
      </CardContent>
    </Card>
  )
}

// ─── 骨架屏 ─────────────────────────────────────────────────────────────────

function ProfileSkeleton() {
  return (
    <div className="mx-auto max-w-4xl space-y-8 p-6">
      <div className="flex items-center gap-4">
        <Skeleton className="size-16 rounded-full" />
        <div className="space-y-2">
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-4 w-48" />
        </div>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={`skel-${i}`}>
            <CardHeader className="pb-2">
              <Skeleton className="h-4 w-24" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-16 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
