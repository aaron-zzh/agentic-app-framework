/**
 * 3D 基础场景容器
 * 注意：需要安装 @react-three/fiber @react-three/drei three 后替换为真实实现
 * 安装命令：pnpm add @react-three/fiber @react-three/drei three --filter @aaf/webui
 */

"use client"

import { Skeleton } from "@/components/ui/skeleton"

interface ThreeSceneProps {
  className?: string
  children?: React.ReactNode
}

export function ThreeScene({ className }: ThreeSceneProps) {
  return (
    <div className={className ?? "flex size-full items-center justify-center bg-muted/20"}>
      <div className="flex flex-col items-center gap-3 text-center">
        <Skeleton className="size-20 rounded-xl" />
        <p className="text-xs text-muted-foreground">
          3D 场景（安装 @react-three/fiber 后启用）
        </p>
      </div>
    </div>
  )
}
