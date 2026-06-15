/**
 * 通知偏好设置——Activity / Application 两分组 + 频道矩阵
 * 参考 minimal-ui AccountNotifications
 * @author AaronZZH
 */

"use client"

import { useEffect, useId, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
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

// ─── Activity + Application 分组开关（对标参考实现） ─────────────────────────

const NOTIFICATION_GROUPS = [
  {
    id: "activity",
    heading: "动态通知",
    caption: "当有人与你互动时通知你",
    items: [
      { id: "activity_comments", label: "有人评论我的内容时通知我" },
      { id: "activity_answers", label: "有人回复我的提问时通知我" },
      { id: "activity_follows", label: "有人关注我时通知我" }
    ]
  },
  {
    id: "application",
    heading: "应用通知",
    caption: "产品更新与内容推送",
    items: [
      { id: "application_news", label: "新闻与公告" },
      { id: "application_product", label: "每周产品更新" },
      { id: "application_blog", label: "每周博客摘要" }
    ]
  }
]

// ─── 业务频道矩阵分组 ─────────────────────────────────────────────────────────

const BUSSINESS_CATEGORIES = ["system", "task", "mention", "subscription"] as const
type BizCategory = (typeof BUSSINESS_CATEGORIES)[number]

const CATEGORY_LABELS: Record<BizCategory, string> = {
  system: "系统通知",
  task: "业务提醒",
  mention: "协作通知",
  subscription: "变更订阅"
}

const CHANNELS: NotificationChannel[] = ["inApp", "email"]
const CHANNEL_LABELS: Record<NotificationChannel, string> = { inApp: "站内", email: "邮件" }

const DEFAULT_CHANNELS: ChannelConfig = { inApp: true, email: false }

function buildDefault(): NotificationPreference {
  const preferences: Record<string, ChannelConfig> = {}
  for (const cat of BUSSINESS_CATEGORIES) {
    preferences[cat] = { ...DEFAULT_CHANNELS }
  }
  return { preferences, quietStart: undefined, quietEnd: undefined }
}

// ─── 组件 ─────────────────────────────────────────────────────────────────────

export default function NotificationSettingsPage() {
  const uid = useId()
  const { data, isLoading } = useNotificationPreference()
  const { mutate: update, isPending } = useUpdateNotificationPreference()

  // 活动通知开关状态（纯本地，提交时合并）
  const [activitySelected, setActivitySelected] = useState<string[]>([
    "activity_comments",
    "application_product"
  ])

  const [form, setForm] = useState<NotificationPreference | null>(null)

  useEffect(() => {
    if (data && !form) {
      const merged = buildDefault()
      if (data.preferences) {
        for (const cat of BUSSINESS_CATEGORIES) {
          if (data.preferences[cat]) merged.preferences[cat] = data.preferences[cat]
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

  const toggleActivity = (id: string) => {
    setActivitySelected((prev) =>
      prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]
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
    <div className="max-w-2xl space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-2xl">通知设置</h1>
        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? "保存中..." : "保存"}
        </Button>
      </div>

      {/* ── Activity / Application 分组 ── */}
      <Card>
        <CardContent className="divide-y p-0">
          {NOTIFICATION_GROUPS.map((group) => (
            <div key={group.id} className="grid grid-cols-1 gap-4 p-6 md:grid-cols-[1fr_2fr]">
              {/* 左：标题说明 */}
              <div>
                <p className="font-semibold">{group.heading}</p>
                <p className="mt-0.5 text-muted-foreground text-sm">{group.caption}</p>
              </div>
              {/* 右：开关列表 */}
              <div className="space-y-3 rounded-lg bg-muted/40 p-4">
                {group.items.map((item) => (
                  <div key={item.id} className="flex items-center justify-between">
                    <Label htmlFor={item.id} className="cursor-pointer font-normal text-sm">
                      {item.label}
                    </Label>
                    <Switch
                      id={item.id}
                      checked={activitySelected.includes(item.id)}
                      onCheckedChange={() => toggleActivity(item.id)}
                    />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* ── 业务频道矩阵 ── */}
      <div>
        <p className="mb-3 font-semibold">频道偏好</p>
        <div className="rounded-lg border">
          <div className="grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 border-b bg-muted/50 px-4 py-2">
            <span className="font-medium text-sm">通知类别</span>
            {CHANNELS.map((ch) => (
              <span key={ch} className="text-center font-medium text-sm">
                {CHANNEL_LABELS[ch]}
              </span>
            ))}
          </div>
          {BUSSINESS_CATEGORIES.map((cat, idx) => (
            <div
              key={cat}
              className={`grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 px-4 py-3 ${idx < BUSSINESS_CATEGORIES.length - 1 ? "border-b" : ""}`}
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
      </div>

      <Separator />

      {/* ── 免打扰时段 ── */}
      <div className="space-y-4">
        <div>
          <p className="font-semibold">免打扰时段</p>
          <p className="text-muted-foreground text-sm">在此时段内不发送推送</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label htmlFor={`${uid}-quiet-start`}>开始</Label>
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
            <Label htmlFor={`${uid}-quiet-end`}>结束</Label>
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
