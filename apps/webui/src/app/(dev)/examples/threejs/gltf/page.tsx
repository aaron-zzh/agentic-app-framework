"use client"

/**
 * GLTF 模型加载示例页
 * @author AaronZZH & Kiro
 */

import dynamic from "next/dynamic"

const GLTFScene = dynamic(() => import("./_components/GLTFScene").then((m) => m.GLTFScene), {
  ssr: false
})

export default function GLTFPage() {
  return (
    <div className="p-6">
      <h2 className="mb-2 font-bold text-xl">GLTF 模型加载</h2>
      <p className="mb-4 text-muted-foreground text-sm">
        GLTFLoader + HDR 环境光 + 动画播放 + 轨道控制。
      </p>
      <GLTFScene />
    </div>
  )
}
