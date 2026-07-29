/**
 * Content Studio 首页——项目类型优先创建入口。
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useEffect } from "react"
import { SectionHaze } from "@/components/studio"
import { NewProjectLauncher, RecentProjectGrid } from "@/features/studio/content"
import { HomeDataCapsules, HomeRecentAssets } from "@/features/studio/home"

const WELCOME_KEY = "aaf:lastWelcomeAt"
const WELCOME_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000

export default function StudioHomePage() {
  const router = useRouter()

  useEffect(() => {
    const last = localStorage.getItem(WELCOME_KEY)
    const expired = !last || Date.now() - Number(last) > WELCOME_INTERVAL_MS
    if (expired) router.replace("/studio/welcome")
  }, [router])

  return (
    <div className="relative">
      <SectionHaze variant="blend" />
      <div className="relative mx-auto flex max-w-7xl flex-col gap-8 px-6 py-8">
        <section className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="font-semibold text-2xl">把业务目标变成内容项目</h1>
            <p className="text-muted-foreground text-sm">
              先选择这次要完成的任务，Assistant 会在项目内补齐、生成并推进。
            </p>
          </div>
          <HomeDataCapsules />
        </section>
        <NewProjectLauncher />
        <RecentProjectGrid />
        <HomeRecentAssets />
      </div>
    </div>
  )
}
