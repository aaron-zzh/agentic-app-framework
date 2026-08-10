/**
 * Content Studio 首页——统计、最近项目、蓝图与快速创作。
 * @author AaronZZH & Kiro
 */

"use client"

import { FileText, ImageIcon, Mic, Music, UserRound, Video } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect } from "react"
import { GlassCard, SectionHaze } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { RecentProjectGrid } from "@/features/studio/content"
import { HomeDataCapsules } from "@/features/studio/home"
import { HomeBlueprintSection } from "@/features/studio/home/HomeBlueprintSection"

const WELCOME_KEY = "aaf:lastWelcomeAt"
const WELCOME_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000

const QUICK_CREATE_ENTRIES = [
  {
    title: "图像",
    href: "/studio/create?mode=image",
    icon: ImageIcon
  },
  {
    title: "视频",
    href: "/studio/create?mode=video",
    icon: Video
  },
  {
    title: "文案",
    href: "/studio/create/copy",
    icon: FileText
  },
  {
    title: "配音",
    href: "/studio/create?mode=voice",
    icon: Mic
  },
  {
    title: "音乐",
    href: "/studio/create?mode=music",
    icon: Music
  },
  {
    title: "数字人",
    href: "/studio/create?mode=digital-human",
    icon: UserRound,
    comingSoon: true
  }
] as const

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
        <section aria-label="工作概况">
          <HomeDataCapsules />
        </section>

        <RecentProjectGrid />

        <HomeBlueprintSection />

        <section aria-labelledby="studio-quick-create-title" className="flex flex-col gap-3">
          <h2 id="studio-quick-create-title" className="font-semibold text-base">
            快速创作
          </h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
            {QUICK_CREATE_ENTRIES.map((entry) => {
              const Icon = entry.icon
              return (
                <Link
                  key={entry.href}
                  href={entry.href}
                  className="group block rounded-2xl outline-none focus-visible:ring-3 focus-visible:ring-primary"
                >
                  <GlassCard interactive className="h-full">
                    <div className="p-4">
                      <div className="flex items-center gap-2">
                        <span className="rounded-xl bg-primary/10 p-2 text-primary">
                          <Icon />
                        </span>
                        <h3 className="font-semibold text-sm">{entry.title}</h3>
                        {"comingSoon" in entry ? (
                          <Badge variant="secondary" className="ml-auto">
                            尚未开放
                          </Badge>
                        ) : null}
                      </div>
                    </div>
                  </GlassCard>
                </Link>
              )
            })}
          </div>
        </section>
      </div>
    </div>
  )
}
