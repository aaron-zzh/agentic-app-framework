/**
 * Three.js WebGL 视频纹理示例页
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"

const VideoScene = dynamic(() => import("./_components/VideoScene").then((m) => m.VideoScene), {
  ssr: false
})

export default function VideoPage() {
  return (
    <div className="p-6">
      <h2 className="mb-2 font-bold text-xl">WebGL 视频纹理</h2>
      <p className="mb-4 text-muted-foreground text-sm">
        200 个方块贴上视频纹理，色相随时间旋转，周期性爆炸/复位。鼠标移动控制视角。
      </p>
      <VideoScene />
    </div>
  )
}
