/**
 * 通知偏好设置页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useId, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { TypographyH1 } from "@/components/ui/typography"
import type { NotificationChannel, NotificationPreference } from "@/lib/api/notification-preference"
import { notify } from "@/lib/notification"
import {
  useNotificationPreference,
  useUpdateNotificationPreference
} from "@/lib/queries/use-notification-preference"

const TYPE_LABELS: Record<string, string> = {
  approval: "审批通知",
  system: "系统通知",
  task: "业务提醒",
  mention: "协作通知",
  change: "变更通知"
}

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  inApp: "站内",
  email: "邮件",
  wechat: "企微"
}

const CHANNELS: NotificationChannel[] = ["inApp", "email", "wechat"]

export default function NotificationSettingsPage() {
  const uid = useId()
  const quietStartId = `${uid}-quiet-start`
  const quietEndId = `${uid}-quiet-end`
  const { data, isLoading } = useNotificationPreference()
  const { mutate: update, isPending } = useUpdateNotificationPreference()
  const [form, setForm] = useState<NotificationPreference | null>(null)

  // 数据加载后初始化表单
  useEffect(() => {
    if (data && !form) setForm(data)
  }, [data, form])

  if (isLoading || !form) {
    return (
      <PageContainer>
        <Skeleton className="mb-6 h-8 w-48" />
        <div className="space-y-4">
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      </PageContainer>
    )
  }

  const toggleChannel = (type: string, channel: NotificationChannel, value: boolean) => {
    setForm((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        items: prev.items.map((item) =>
          item.type === type ? { ...item, channels: { ...item.channels, [channel]: value } } : item
        )
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
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1 className="text-2xl">通知设置</TypographyH1>
        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? "保存中..." : "保存"}
        </Button>
      </div>

      {/* 通知类别 × 通道矩阵 */}
      <div className="rounded-lg border">
        {/* 表头 */}
        <div className="grid grid-cols-[1fr_repeat(3,80px)] items-center gap-4 border-b bg-muted/50 px-4 py-2">
          <span className="font-medium text-sm">通知类别</span>
          {CHANNELS.map((ch) => (
            <span key={ch} className="text-center font-medium text-sm">
              {CHANNEL_LABELS[ch]}
            </span>
          ))}
        </div>

        {form.items.map((item, idx) => (
          <div
            key={item.type}
            className={`grid grid-cols-[1fr_repeat(3,80px)] items-center gap-4 px-4 py-3 ${idx < form.items.length - 1 ? "border-b" : ""}`}
          >
            <span className="text-sm">{TYPE_LABELS[item.type] ?? item.type}</span>
            {CHANNELS.map((ch) => (
              <div key={ch} className="flex justify-center">
                <Switch
                  checked={item.channels[ch]}
                  onCheckedChange={(v) => toggleChannel(item.type, ch, v)}
                  aria-label={`${TYPE_LABELS[item.type] ?? item.type} ${CHANNEL_LABELS[ch]}`}
                />
              </div>
            ))}
          </div>
        ))}
      </div>

      <Separator className="my-6" />

      {/* 免打扰时段 */}
      <div className="space-y-4">
        <h2 className="font-medium text-base">免打扰时段</h2>
        <p className="text-muted-foreground text-sm">在此时段内不发送通知（站内通知除外）</p>
        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label htmlFor={quietStartId}>开始时间</Label>
            <Input
              id={quietStartId}
              type="time"
              value={form.quietStart ?? ""}
              onChange={(e) => setForm((prev) => prev && { ...prev, quietStart: e.target.value })}
              className="w-36"
            />
          </div>
          <span className="mt-6 text-muted-foreground">至</span>
          <div className="space-y-1">
            <Label htmlFor={quietEndId}>结束时间</Label>
            <Input
              id={quietEndId}
              type="time"
              value={form.quietEnd ?? ""}
              onChange={(e) => setForm((prev) => prev && { ...prev, quietEnd: e.target.value })}
              className="w-36"
            />
          </div>
        </div>
      </div>
    </PageContainer>
  )
}
