/**
 * 3D 模型查看器
 * 注意：需要安装 @react-three/fiber @react-three/drei three 后替换为真实实现
 */

"use client"

import { Skeleton } from "@/components/ui/skeleton"

interface ModelViewerProps {
  modelUrl: string
  className?: string
}

export function ModelViewer({ modelUrl, className }: ModelViewerProps) {
  return (
    <div className={className ?? "flex size-full items-center justify-center bg-muted/20"}>
      <div className="flex flex-col items-center gap-3 text-center">
        <Skeleton className="size-20 rounded-xl" />
        <p className="text-xs text-muted-foreground">3D 模型: {modelUrl.split("/").pop()}</p>
      </div>
    </div>
  )
}
