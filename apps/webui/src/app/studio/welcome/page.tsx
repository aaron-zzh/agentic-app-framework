/**
 * /studio/welcome——落地动画（M5）
 * fixed 全屏覆盖 Studio 外壳，2.5 秒后自动跳转首屏
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useEffect } from "react"
import { SectionHaze } from "@/components/studio"
import { $url } from "@/lib/utils"

const WELCOME_KEY = "aaf:lastWelcomeAt"

export default function StudioWelcomePage() {
  const router = useRouter()

  useEffect(() => {
    localStorage.setItem(WELCOME_KEY, String(Date.now()))
    const timer = setTimeout(() => router.replace("/studio"), 2500)
    return () => clearTimeout(timer)
  }, [router])

  return (
    <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center overflow-hidden bg-background">
      <SectionHaze variant="violet" className="opacity-60" />

      <div className="relative flex size-[140px] items-center justify-center">
        <div className="animate-[pulse_2s_ease-in-out_infinite]">
          {/* biome-ignore lint/performance/noImgElement: welcome splash */}
          <img src={$url.cdn("/logo/logo.png")} alt="AAF Studio" className="size-20" />
        </div>
        <span className="absolute inset-0 animate-[spin_4s_linear_infinite] rounded-[25%] border-[3px] border-violet-500/30" />
        <span className="absolute inset-3 animate-[spin_4s_linear_infinite_reverse] rounded-[25%] border-[5px] border-cyan-500/20" />
      </div>

      <div className="mt-8 text-center">
        <h1 className="font-bold text-2xl tracking-tight">正在进入驾驶舱</h1>
        <p className="mt-2 text-muted-foreground text-sm">AI 原生创作工作室</p>
      </div>

      {/* 进度条 */}
      <div className="relative mt-6 h-0.5 w-32 overflow-hidden rounded-full bg-foreground/10">
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-violet-500 to-cyan-500"
          style={{ animation: "welcomeProgress 2.5s linear forwards" }}
        />
      </div>
      <style>{`@keyframes welcomeProgress { from { width: 0% } to { width: 100% } }`}</style>
    </div>
  )
}
