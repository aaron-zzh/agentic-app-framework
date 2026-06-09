/**
 * 粒子背景对比页：CSS3DSprite vs R3F WebGL + Bloom
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ParticlesCSS3D } from "./_components/ParticlesCSS3D"

const ParticlesR3F = dynamic(
  () => import("@/components/three/ParticlesR3F").then((m) => m.ParticlesR3F),
  { ssr: false }
)

export default function ParticlesComparePage() {
  const [bloom, setBloom] = useState(false)

  return (
    <div className="flex flex-col gap-6 p-6">
      <h2 className="font-bold text-xl">粒子背景方案对比</h2>

      <div>
        <p className="mb-2 font-medium text-sm">CSS3DSprite</p>
        <div className="relative h-[480px] w-full overflow-hidden rounded-lg">
          <ParticlesCSS3D />
        </div>
      </div>

      <div>
        <div className="mb-2 flex items-center gap-3">
          <p className="font-medium text-sm">R3F WebGL（首页方案）</p>
          <Button
            size="sm"
            variant={bloom ? "default" : "outline"}
            onClick={() => setBloom((v) => !v)}
          >
            Bloom {bloom ? "ON" : "OFF"}
          </Button>
        </div>
        <div className="h-[480px] w-full overflow-hidden rounded-lg">
          <ParticlesR3F bloom={bloom} />
        </div>
      </div>
    </div>
  )
}
