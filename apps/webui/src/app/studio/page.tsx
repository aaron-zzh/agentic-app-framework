/**
 * Studio 驾驶舱首屏
 *
 * 顶部数据胶囊 + 统一对话入口卡 + 项目网格
 * M5: 首次 + 间隔7天 → 跳转落地动画
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useEffect } from "react"
import { SectionHaze } from "@/components/studio"
import {
  HomeChatLauncher,
  HomeDataCapsules,
  HomeProjectGrid,
  HomeRecentAssets
} from "@/features/studio/home"

const WELCOME_KEY = "aaf:lastWelcomeAt"
const WELCOME_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000

export default function StudioHomePage() {
  const router = useRouter()

  useEffect(() => {
    const last = localStorage.getItem(WELCOME_KEY)
    const expired = !last || Date.now() - Number(last) > WELCOME_INTERVAL_MS
    if (expired) {
      router.replace("/studio/welcome")
    }
  }, [router])

  return (
    <div className="relative">
      <SectionHaze variant="blend" />
      <div className="relative mx-auto max-w-7xl space-y-8 px-6 py-8">
        <section className="space-y-4">
          <div>
            <h1 className="font-semibold text-2xl">欢迎回来 👋</h1>
          </div>
          <HomeDataCapsules />
        </section>
        <HomeChatLauncher />
        <HomeRecentAssets />
        <HomeProjectGrid />
      </div>
    </div>
  )
}
