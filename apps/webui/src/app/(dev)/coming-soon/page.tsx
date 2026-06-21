/**
 * Coming Soon——即将上线页面（含邮箱订阅入口）
 */

"use client"

import { type FormEvent, useEffect, useState } from "react"
import { toast } from "sonner"

import { leadApi } from "@/lib/api/rest/lead/lead"
import { $url } from "@/lib/utils"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"

const TARGET_DATE = new Date("2026-08-20T20:30:00")

function useCountdown(target: Date) {
  const [time, setTime] = useState<ReturnType<typeof getRemaining> | null>(null)

  useEffect(() => {
    setTime(getRemaining(target))
    const timer = setInterval(() => setTime(getRemaining(target)), 1000)
    return () => clearInterval(timer)
  }, [target])

  return time
}

function getRemaining(target: Date) {
  const diff = Math.max(0, target.getTime() - Date.now())
  return {
    days: String(Math.floor(diff / 86400000)).padStart(2, "0"),
    hours: String(Math.floor((diff % 86400000) / 3600000)).padStart(2, "0"),
    minutes: String(Math.floor((diff % 3600000) / 60000)).padStart(2, "0"),
    seconds: String(Math.floor((diff % 60000) / 1000)).padStart(2, "0")
  }
}

export default function ComingSoonPage() {
  const countdown = useCountdown(TARGET_DATE)
  const [email, setEmail] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const handleSubscribe = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!email.trim()) return
    setSubmitting(true)
    try {
      await leadApi.create({
        anonymousId: getOrCreateAnonymousId(),
        channel: "NEWSLETTER",
        email: email.trim()
      })
      setSubmitted(true)
      toast.success("订阅成功，上线时第一时间通知您")
    } catch {
      toast.error("订阅失败，请稍后再试")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-8 p-6 pt-40 text-center">
      <div>
        <h1 className="font-bold text-3xl">即将上线！</h1>
        <p className="mt-2 text-muted-foreground">我们正在努力开发中，敬请期待。</p>
      </div>

      {/* 插画（SVG 背景 + webp 火箭叠加） */}
      <div className="relative flex items-center justify-center">
        <svg viewBox="0 0 480 360" className="h-auto w-64 max-w-full" aria-hidden="true">
          <ellipse cx="240" cy="300" rx="180" ry="30" className="fill-muted/40" />
          <circle cx="120" cy="100" r="3" className="fill-primary/40" />
          <circle cx="350" cy="80" r="4" className="fill-primary/30" />
          <circle cx="100" cy="200" r="2" className="fill-primary/20" />
          <circle cx="380" cy="180" r="3" className="fill-primary/25" />
          <circle cx="300" cy="120" r="2" className="fill-primary/35" />
          <circle cx="160" cy="280" r="5" className="fill-primary/15" />
          <circle cx="340" cy="260" r="4" className="fill-primary/20" />
        </svg>
        {/* biome-ignore lint/performance/noImgElement: 静态插画无需 next/image 优化 */}
        <img
          src={$url.cdn("/assets/illustrations/illustration-rocket-large.webp")}
          alt=""
          className="absolute h-auto w-48"
        />
      </div>

      {/* 倒计时 */}
      {countdown && (
        <div className="flex items-center gap-2 font-bold text-4xl sm:gap-4 sm:text-5xl">
          <TimeBlock value={countdown.days} label="天" />
          <span className="text-muted-foreground">:</span>
          <TimeBlock value={countdown.hours} label="时" />
          <span className="text-muted-foreground">:</span>
          <TimeBlock value={countdown.minutes} label="分" />
          <span className="text-muted-foreground">:</span>
          <TimeBlock value={countdown.seconds} label="秒" />
        </div>
      )}

      {/* 邮件订阅 */}
      {submitted ? (
        <p className="text-muted-foreground text-sm">
          已记录您的邮箱：<span className="font-medium text-foreground">{email}</span>
        </p>
      ) : (
        <form onSubmit={handleSubscribe} className="flex w-full max-w-md gap-2">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="输入邮箱，获取上线通知"
            required
            disabled={submitting}
            className="flex-1 rounded-md border bg-background px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/30 disabled:opacity-60"
          />
          <button
            type="submit"
            disabled={submitting}
            className="shrink-0 rounded-md bg-primary px-5 py-2.5 font-medium text-primary-foreground text-sm hover:bg-primary/90 disabled:opacity-60"
          >
            {submitting ? "提交中..." : "通知我"}
          </button>
        </form>
      )}
    </main>
  )
}

function TimeBlock({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex items-baseline gap-1">
      <span className="inline-block w-[2ch] text-center font-mono tabular-nums">{value}</span>
      <span className="font-normal text-muted-foreground text-sm">{label}</span>
    </div>
  )
}
