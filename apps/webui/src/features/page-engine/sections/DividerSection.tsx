/**
 * DividerSection — 视觉分割区：支持 video / three-waves 两种模式
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"

import { ScrollDownHint } from "../components/ScrollDownHint"
import type { SectionComponentProps } from "../types"

const WavesScene = dynamic(
  () => import("@/components/three/WavesScene").then((m) => m.WavesScene),
  { ssr: false }
)

interface DividerProps {
  mode?: "video" | "waves"
  src?: string
  height?: string
  scrollHint?: boolean
}

export function DividerSection({ data }: SectionComponentProps) {
  const {
    mode = "video",
    src = "/assets/videos/bg.mp4",
    height = "40vh",
    scrollHint
  } = data as DividerProps

  return (
    <div className="relative w-full overflow-hidden" style={{ height }}>
      {mode === "video" && (
        <video className="h-full w-full object-cover" src={src} autoPlay muted loop playsInline />
      )}
      {mode === "waves" && <WavesScene />}
      {scrollHint && (
        <div className="absolute bottom-4 left-0 flex w-full justify-center">
          <ScrollDownHint />
        </div>
      )}
    </div>
  )
}
