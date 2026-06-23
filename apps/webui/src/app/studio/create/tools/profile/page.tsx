/**
 * 工具-用户画像
 * @author AaronZZH & Kiro
 */

"use client"

import { User } from "lucide-react"
import { DataCapsule, GlassCard } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { useProfile } from "@/lib/api/rest/user/profile"

export default function ProfileToolPage() {
  const { data: profile, isLoading } = useProfile()

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <User className="size-5 text-emerald-400" />
        <h1 className="font-semibold text-xl">用户画像</h1>
      </header>

      <GlassCard glow="accent">
        <div className="space-y-4 p-5">
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-4 w-40" />
            </div>
          ) : profile ? (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                {profile.avatar ? (
                  // biome-ignore lint/performance/noImgElement: 头像 URL
                  <img
                    src={profile.avatar}
                    alt="头像"
                    className="size-14 rounded-full object-cover"
                  />
                ) : (
                  <div className="flex size-14 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 text-xl">
                    {profile.nickname?.[0] ?? profile.username?.[0] ?? "U"}
                  </div>
                )}
                <div>
                  <p className="font-semibold text-base">{profile.nickname || profile.username}</p>
                  <p className="text-muted-foreground text-sm">@{profile.username}</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <DataCapsule label="邮箱" value={profile.email || "未设置"} />
                <DataCapsule label="手机" value={profile.phone || "未绑定"} />
                <DataCapsule label="注册时间" value={profile.createTime?.slice(0, 10) ?? "-"} />
                <DataCapsule label="用户 ID" value={`#${profile.id}`} />
              </div>
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">暂无数据</p>
          )}
        </div>
      </GlassCard>
    </div>
  )
}
