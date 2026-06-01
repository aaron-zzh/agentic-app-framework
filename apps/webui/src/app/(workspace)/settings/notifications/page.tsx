/**
 * 通知偏好设置页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useId, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import type {
  ChannelConfig,
  NotificationChannel,
  NotificationPreference
} from "@/lib/api/rest/user/notification-preference"
import { notify } from "@/lib/notification"
import {
  useNotificationPreference,
  useUpdateNotificationPreference
} from "@/lib/queries/use-notification-preference"

/** 通知类别定义 */
const CATEGORIES = ["system", "task", "mention", "subscription"] as const

const CATEGORY_LABELS: Record<string, string> = {
  system: "系统通知",
  task: "业务提醒",
  mention: "协作通知",
  subscription: "变更订阅"
}

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  inApp: "站内",
  email: "邮件"
}

const CHANNELS: NotificationChannel[] = ["inApp", "email"]

/** 默认通道配置 */
const DEFAULT_CHANNELS: ChannelConfig = { inApp: true, email: false }

/** 构建默认偏好（后端返回空时使用） */
function buildDefault(): NotificationPreference {
  const preferences: Record<string, ChannelConfig> = {}
  for (const cat of CATEGORIES) {
    preferences[cat] = { ...DEFAULT_CHANNELS }
  }
  return { preferences, quietStart: undefined, quietEnd: undefined }
}

export default function NotificationSettingsPage() {
  const uid = useId()
  const { data, isLoading } = useNotificationPreference()
  const { mutate: update, isPending } = useUpdateNotificationPreference()
  const [form, setForm] = useState<NotificationPreference | null>(null)

  // 数据加载后初始化表单
  useEffect(() => {
    if (data && !form) {
      // 后端可能返回空 preferences，补全缺失类别
      const merged = buildDefault()
      if (data.preferences) {
        for (const cat of CATEGORIES) {
          if (data.preferences[cat]) {
            merged.preferences[cat] = data.preferences[cat]
          }
        }
      }
      merged.quietStart = data.quietStart
      merged.quietEnd = data.quietEnd
      setForm(merged)
    }
  }, [data, form])

  if (isLoading || !form) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        {[1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    )
  }

  const toggleChannel = (category: string, channel: NotificationChannel, value: boolean) => {
    setForm((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        preferences: {
          ...prev.preferences,
          [category]: { ...prev.preferences[category], [channel]: value }
        }
      }
    })
  }

  const handleSave = () => {
    update(form, {
      onSuccess: () => notify.success("通知偏好已保存"),
      onError: () => notify.error("保存失败，请重试")
    })
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-2xl">通知设置</h1>
        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? "保存中..." : "保存"}
        </Button>
      </div>

      {/* 通知类别 × 通道矩阵 */}
      <div className="rounded-lg border">
        {/* 表头 */}
        <div className="grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 border-b bg-muted/50 px-4 py-2">
          <span className="font-medium text-sm">通知类别</span>
          {CHANNELS.map((ch) => (
            <span key={ch} className="text-center font-medium text-sm">
              {CHANNEL_LABELS[ch]}
            </span>
          ))}
        </div>

        {CATEGORIES.map((cat, idx) => (
          <div
            key={cat}
            className={`grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 px-4 py-3 ${idx < CATEGORIES.length - 1 ? "border-b" : ""}`}
          >
            <span className="text-sm">{CATEGORY_LABELS[cat]}</span>
            {CHANNELS.map((ch) => (
              <div key={ch} className="flex justify-center">
                <Switch
                  checked={form.preferences[cat]?.[ch] ?? false}
                  onCheckedChange={(v) => toggleChannel(cat, ch, v)}
                  aria-label={`${CATEGORY_LABELS[cat]} ${CHANNEL_LABELS[ch]}`}
                />
              </div>
            ))}
          </div>
        ))}
      </div>

      <Separator />

      {/* 免打扰时段 */}
      <div className="space-y-4">
        <h2 className="font-medium text-base">免打扰时段</h2>
        <p className="text-muted-foreground text-sm">在此时段内不发送通知推送</p>
        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label htmlFor={`${uid}-quiet-start`}>开始时间</Label>
            <Input
              id={`${uid}-quiet-start`}
              type="time"
              value={form.quietStart ?? ""}
              onChange={(e) => setForm((prev) => prev && { ...prev, quietStart: e.target.value })}
              className="w-36"
            />
          </div>
          <span className="mt-6 text-muted-foreground">至</span>
          <div className="space-y-1">
            <Label htmlFor={`${uid}-quiet-end`}>结束时间</Label>
            <Input
              id={`${uid}-quiet-end`}
              type="time"
              value={form.quietEnd ?? ""}
              onChange={(e) => setForm((prev) => prev && { ...prev, quietEnd: e.target.value })}
              className="w-36"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
