"use client"

/**
 * 粒子波浪示例页
 * @author AaronZZH & Kiro
 */

import dynamic from "next/dynamic"

const WavesScene = dynamic(() => import("@/components/three").then((m) => m.WavesScene), {
  ssr: false
})

export default function WavesPage() {
  return (
    <div className="flex flex-col gap-4 p-6">
      <div>
        <h2 className="font-bold text-xl">粒子波浪</h2>
        <p className="mt-1 text-muted-foreground text-sm">
          2500 个粒子（50×50）构成正弦波浪场，自定义 GLSL
          着色器渲染圆形粒子，鼠标移动驱动摄像机视角追踪。
        </p>
      </div>
      <div className="h-[600px] w-full overflow-hidden rounded-lg">
        <WavesScene />
      </div>
    </div>
  )
}
