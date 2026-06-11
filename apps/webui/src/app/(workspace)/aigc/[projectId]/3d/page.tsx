/**
 * 3D 模型生成工作台
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import { Card } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

const BirdsScene = dynamic(() => import("./BirdsScene"), {
  ssr: false,
  loading: () => <SceneLoading />
})
const ModelViewerScene = dynamic(() => import("./ModelViewerScene"), {
  ssr: false,
  loading: () => <SceneLoading />
})

function SceneLoading() {
  return (
    <div className="flex size-full items-center justify-center bg-muted/20">
      <Skeleton className="size-20 rounded-xl" />
    </div>
  )
}

export default function AigcThreeDPage() {
  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <div>
        <h1 className="font-bold text-2xl">3D 生成</h1>
        <p className="text-muted-foreground text-sm">AI 生成 3D 模型预览与展示</p>
      </div>

      <Tabs defaultValue="viewer" className="flex-1">
        <TabsList>
          <TabsTrigger value="viewer">模型查看器</TabsTrigger>
          <TabsTrigger value="birds">动画鸟群</TabsTrigger>
        </TabsList>

        <TabsContent value="viewer" className="h-[calc(100%-40px)]">
          <Card className="size-full overflow-hidden">
            <ModelViewerScene />
          </Card>
        </TabsContent>

        <TabsContent value="birds" className="h-[calc(100%-40px)]">
          <Card className="size-full overflow-hidden">
            <BirdsScene />
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
