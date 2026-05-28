/**
 * 3D 模型展示示例页面——动画鸟群 + 模型查看器
 * 参考 tmp/nextjs/xueji/apps/demo/src/app/threejs/BirdsPage.tsx
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import { Suspense, useMemo, useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Card } from "@/components/ui/card"

/** 动态导入 3D 场景避免 SSR */
const BirdsScene = dynamic(() => import("./BirdsScene"), { ssr: false, loading: () => <SceneLoading /> })
const ModelViewerScene = dynamic(() => import("./ModelViewerScene"), { ssr: false, loading: () => <SceneLoading /> })

function SceneLoading() {
  return (
    <div className="flex size-full items-center justify-center bg-muted/20">
      <Skeleton className="size-20 rounded-xl" />
    </div>
  )
}

export default function ThreeDemoPage() {
  return (
    <div className="flex h-[calc(100vh-var(--layout-header-height))] flex-col gap-4 p-6">
      <div>
        <h1 className="font-bold text-2xl">3D 展示</h1>
        <p className="text-muted-foreground text-sm">react-three-fiber 示例 + AI 生成 3D 模型预览</p>
      </div>

      <Tabs defaultValue="birds" className="flex-1">
        <TabsList>
          <TabsTrigger value="birds">动画鸟群</TabsTrigger>
          <TabsTrigger value="viewer">模型查看器</TabsTrigger>
        </TabsList>

        <TabsContent value="birds" className="h-[calc(100%-40px)]">
          <Card className="size-full overflow-hidden">
            <BirdsScene />
          </Card>
        </TabsContent>

        <TabsContent value="viewer" className="h-[calc(100%-40px)]">
          <Card className="size-full overflow-hidden">
            <ModelViewerScene />
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
