/**
 * 粒子背景对比页：CSS3DSprite vs R3F WebGL + Bloom
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"

import { HeroParticlesBackground } from "@/features/page-engine/sections/HeroParticlesBackground"

const ParticlesR3F = dynamic(
  () => import("./_components/ParticlesR3F").then((m) => m.ParticlesR3F),
  { ssr: false }
)

export default function ParticlesComparePage() {
  return (
    <div className="flex flex-col gap-6 p-6">
      <h2 className="font-bold text-xl">粒子背景方案对比</h2>

      <div>
        <p className="mb-2 font-medium text-sm">CSS3DSprite（当前首页方案）</p>
        <div className="relative h-[480px] w-full overflow-hidden rounded-lg">
          <HeroParticlesBackground />
        </div>
      </div>

      <div>
        <p className="mb-2 font-medium text-sm">R3F WebGL + Bloom（备选方案）</p>
        <div className="h-[480px] w-full overflow-hidden rounded-lg">
          <ParticlesR3F />
        </div>
      </div>
    </div>
  )
}
