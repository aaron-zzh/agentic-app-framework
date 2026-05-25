/**
 * 3D 视图容器——动态导入 ThreeScene，工具栏控制
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import { useCallback, useRef, useState } from "react"
import { Camera, Maximize2, RotateCcw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"

/** 动态导入避免 SSR 问题 */
const ThreeScene = dynamic(
  () => import("./ThreeScene").then((m) => ({ default: m.ThreeScene })),
  {
    ssr: false,
    loading: () => (
      <div className="flex size-full items-center justify-center bg-muted/30">
        <Skeleton className="size-16 rounded-lg" />
      </div>
    ),
  }
)

interface ThreeViewProps {
  className?: string
}

export function ThreeView({ className }: ThreeViewProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [key, setKey] = useState(0)

  /** 重置视角（通过重新挂载实现） */
  const handleReset = useCallback(() => {
    setKey((k) => k + 1)
  }, [])

  /** 截图 */
  const handleScreenshot = useCallback(() => {
    const canvas = containerRef.current?.querySelector("canvas")
    if (!canvas) return
    const link = document.createElement("a")
    link.download = "3d-screenshot.png"
    link.href = canvas.toDataURL("image/png")
    link.click()
  }, [])

  /** 全屏 */
  const handleFullscreen = useCallback(() => {
    containerRef.current?.requestFullscreen()
  }, [])

  return (
    <div ref={containerRef} className={className ?? "relative size-full"}>
      <ThreeScene key={key} className="size-full" />

      {/* 工具栏 */}
      <div className="absolute right-3 top-3 flex gap-1">
        <Button
          variant="ghost"
          size="sm"
          className="size-7 bg-background/60 p-0 backdrop-blur-sm hover:bg-background/80"
          onClick={handleReset}
          title="重置视角"
        >
          <RotateCcw className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="size-7 bg-background/60 p-0 backdrop-blur-sm hover:bg-background/80"
          onClick={handleScreenshot}
          title="截图"
        >
          <Camera className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="size-7 bg-background/60 p-0 backdrop-blur-sm hover:bg-background/80"
          onClick={handleFullscreen}
          title="全屏"
        >
          <Maximize2 className="size-3.5" />
        </Button>
      </div>
    </div>
  )
}
