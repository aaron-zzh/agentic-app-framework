/**
 * /studio/me/settings——偏好设置（主题/语言/通知/隐私）
 * 迁移自 workspace/settings/notifications，不跳出 Studio 外壳
 * @author AaronZZH & Kiro
 */

"use client"

import { Trash2 } from "lucide-react"
import { useEffect, useId, useState } from "react"
import { ThemeSettings } from "@/components/common/ThemeSettings"
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

function buildDefault(): NotificationPreference {
  const preferences: Record<string, ChannelConfig> = {}
  for (const cat of BUSSINESS_CATEGORIES) {
    preferences[cat] = { inApp: true, email: false }
  }
  return { preferences, quietStart: undefined, quietEnd: undefined }
}

export default function StudioMeSettingsPage() {
  const uid = useId()
  const { data, isLoading } = useNotificationPreference()
  const { mutate: update, isPending } = useUpdateNotificationPreference()
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
    if (!form) return
    update(form, {
      onSuccess: () => notify.success("设置已保存"),
      onError: () => notify.error("保存失败")
    })
  }

  const handleClearCache = () => {
    localStorage.clear()
    notify.success("本地缓存已清除")
  }

  return (
    <div className="relative mx-auto max-w-2xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-6">
        <h1 className="font-semibold text-xl">偏好设置</h1>

        {/* 主题 */}
        <GlassCard glow="none">
          <GlassCardHeader>
            <GlassCardTitle>主题外观</GlassCardTitle>
          </GlassCardHeader>
          <GlassCardBody>
            <ThemeSettings />
          </GlassCardBody>
        </GlassCard>

        {/* 通知偏好 */}
        <GlassCard glow="none">
          <GlassCardHeader>
            <GlassCardTitle>通知偏好</GlassCardTitle>
          </GlassCardHeader>
          <GlassCardBody>
            {isLoading || !form ? (
              <div className="space-y-2">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={`nf-${i}`} className="h-10" />
                ))}
              </div>
            ) : (
              <div className="space-y-3">
                <div className="grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 border-foreground/[0.06] border-b pb-2">
                  <span className="font-medium text-muted-foreground text-sm">类别</span>
                  {CHANNELS.map((ch) => (
                    <span
                      key={ch}
                      className="text-center font-medium text-muted-foreground text-sm"
                    >
                      {CHANNEL_LABELS[ch]}
                    </span>
                  ))}
                </div>
                {BUSSINESS_CATEGORIES.map((cat) => (
                  <div
                    key={cat}
                    className="grid grid-cols-[1fr_repeat(2,80px)] items-center gap-4 py-2"
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

                {/* 免打扰 */}
                <div className="space-y-3 border-foreground/[0.06] border-t pt-4">
                  <p className="font-medium text-sm">免打扰时段</p>
                  <div className="flex items-center gap-3">
                    <div className="space-y-1">
                      <Label htmlFor={`${uid}-qs`} className="text-xs">
                        开始
                      </Label>
                      <Input
                        id={`${uid}-qs`}
                        type="time"
                        value={form.quietStart ?? ""}
                        onChange={(e) => setForm((p) => p && { ...p, quietStart: e.target.value })}
                        className="w-32 bg-muted/20"
                      />
                    </div>
                    <span className="mt-4 text-muted-foreground text-sm">至</span>
                    <div className="space-y-1">
                      <Label htmlFor={`${uid}-qe`} className="text-xs">
                        结束
                      </Label>
                      <Input
                        id={`${uid}-qe`}
                        type="time"
                        value={form.quietEnd ?? ""}
                        onChange={(e) => setForm((p) => p && { ...p, quietEnd: e.target.value })}
                        className="w-32 bg-muted/20"
                      />
                    </div>
                  </div>
                </div>

                <div className="flex justify-end pt-2">
                  <GlowButton tone="violet" onClick={handleSave} disabled={isPending}>
                    {isPending ? "保存中..." : "保存通知设置"}
                  </GlowButton>
                </div>
              </div>
            )}
          </GlassCardBody>
        </GlassCard>

        {/* 隐私 */}
        <GlassCard glow="none">
          <GlassCardHeader>
            <GlassCardTitle>隐私与数据</GlassCardTitle>
          </GlassCardHeader>
          <GlassCardBody className="space-y-3">
            <p className="text-muted-foreground text-sm">
              清除本地缓存可解决部分显示异常问题，不影响云端数据。
            </p>
            <Button variant="outline" size="sm" className="gap-2" onClick={handleClearCache}>
              <Trash2 className="size-4" />
              清除本地缓存
            </Button>
          </GlassCardBody>
        </GlassCard>
      </div>
    </div>
  )
}
