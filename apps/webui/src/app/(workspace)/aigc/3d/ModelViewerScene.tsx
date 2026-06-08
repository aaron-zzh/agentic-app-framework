/**
 * 模型查看器场景——加载 GLB 模型展示
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ModelViewer } from "@/features/aigc/three/ModelViewer"
import { $url } from "@/lib/utils"

const MODELS = [
  { name: "鸭子", url: $url.cdn("/assets/models/glb/duck.glb") },
  { name: "小狗", url: $url.cdn("/assets/models/glb/dog.glb") },
  { name: "鹳鸟", url: $url.cdn("/assets/models/glb/stork.glb") },
  { name: "鹦鹉", url: $url.cdn("/assets/models/glb/parrot.glb") },
  { name: "火烈鸟", url: $url.cdn("/assets/models/glb/flamingo.glb") }
]

export default function ModelViewerScene() {
  const [activeModel, setActiveModel] = useState(MODELS[0])

  return (
    <div className="flex size-full flex-col">
      {/* 模型选择栏 */}
      <div className="flex gap-2 border-border/50 border-b p-3">
        {MODELS.map((m) => (
          <Button
            key={m.url}
            variant={activeModel.url === m.url ? "default" : "outline"}
            size="sm"
            onClick={() => setActiveModel(m)}
          >
            {m.name}
          </Button>
        ))}
      </div>
      {/* 3D 查看器 */}
      <div className="flex-1">
        <ModelViewer modelUrl={activeModel.url} className="size-full" />
      </div>
    </div>
  )
}
