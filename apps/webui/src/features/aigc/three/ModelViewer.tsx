/**
 * 3D 模型查看器——加载 GLB/glTF 模型并自动居中
 * @author AaronZZH & Kiro
 */

"use client"

import { Center, Html, useGLTF, useProgress } from "@react-three/drei"
import { Suspense, useEffect } from "react"
import { ThreeScene } from "./ThreeScene"

interface ModelViewerProps {
  modelUrl: string
  className?: string
  onLoaded?: () => void
}

/** 加载进度指示器 */
function LoadingIndicator() {
  const { progress } = useProgress()
  return (
    <Html center>
      <div className="flex flex-col items-center gap-2 rounded-lg bg-background/80 px-4 py-3 shadow-lg backdrop-blur-sm">
        <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        <span className="text-muted-foreground text-xs">{progress.toFixed(0)}%</span>
      </div>
    </Html>
  )
}

/** 模型加载组件 */
function Model({ modelUrl, onLoaded }: { modelUrl: string; onLoaded?: () => void }) {
  const { scene } = useGLTF(modelUrl)

  useEffect(() => {
    onLoaded?.()
  }, [onLoaded])

  return (
    <Center>
      <primitive object={scene} />
    </Center>
  )
}

export function ModelViewer({ modelUrl, className, onLoaded }: ModelViewerProps) {
  return (
    <ThreeScene className={className}>
      <Suspense fallback={<LoadingIndicator />}>
        <Model modelUrl={modelUrl} onLoaded={onLoaded} />
      </Suspense>
    </ThreeScene>
  )
}
