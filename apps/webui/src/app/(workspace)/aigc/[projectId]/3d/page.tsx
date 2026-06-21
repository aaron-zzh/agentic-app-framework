/**
 * 3D 模型生成工作台——提交 AigcTask 生成任务，完成后素材自动入库；同时保留模型查看器
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Layers } from "lucide-react"
import dynamic from "next/dynamic"
import { useParams } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { SceneLayout } from "@/components/r3f/SceneLayout"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { AssetCard } from "@/features/aigc/asset/AssetCard"
import type { MediaAssetVO } from "@/features/aigc/types"
import { request } from "@/lib/api/rest/entity/crud"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"

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

interface TaskItem {
  id: number
  prompt: string
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"
  ossUrl?: string
  errorMsg?: string
}

function taskToAsset(task: TaskItem): MediaAssetVO {
  return {
    id: task.id,
    name: task.prompt,
    type: "MODEL_3D",
    url: task.ossUrl ?? "",
    thumbnailUrl: null,
    createTime: new Date().toISOString(),
    generationParams: null,
    size: null,
    width: null,
    height: null,
    tags: null,
    groupId: null,
    groupName: null
  } as unknown as MediaAssetVO
}

export default function AigcThreeDPage() {
  const routeParams = useParams()
  const projectId = routeParams.projectId ? Number(routeParams.projectId) : null
  const [prompt, setPrompt] = useState("")
  const [textureQuality, setTextureQuality] = useState<"none" | "standard" | "detailed">("none")
  const [tasks, setTasks] = useState<TaskItem[]>([])

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MODEL_3D",
          prompt: prompt.trim(),
          projectId: projectId ?? null,
          params: { source: "text", textureQuality }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [{ id: taskId, prompt: prompt.trim(), status: "PENDING" }, ...prev])
      setPrompt("")
      toast.success("3D 生成任务已提交")
    },
    onError: () => {}
  })

  useAigcTaskStream({
    onCompleted: (task) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "SUCCESS", ossUrl: task.ossUrl } : t))
      )
      toast.success("3D 模型生成完成，素材已入库")
    },
    onFailed: (task) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "FAIL", errorMsg: task.errorMsg } : t))
      )
    }
  })

  return (
    <SceneLayout>
      <div className="flex h-full flex-col gap-4 p-6">
        <div>
          <h1 className="font-bold text-2xl">3D 生成</h1>
          <p className="text-muted-foreground text-sm">AI 生成 3D 模型，完成后自动入库</p>
        </div>

        <Tabs defaultValue="generate" className="flex flex-1 flex-col">
          <TabsList>
            <TabsTrigger value="generate">文生 3D</TabsTrigger>
            <TabsTrigger value="viewer">模型查看器</TabsTrigger>
            <TabsTrigger value="birds">动画鸟群</TabsTrigger>
          </TabsList>

          <TabsContent value="generate" className="flex flex-col gap-4">
            {/* 输入区 */}
            <Card className="space-y-4 p-5">
              <div className="space-y-2">
                <Label>描述提示词</Label>
                <Textarea
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="描述你想要生成的 3D 模型（如：一只可爱的小猫，卡通风格）"
                  className="min-h-[80px] resize-none"
                  maxLength={3000}
                />
                <p className="text-right text-muted-foreground text-xs">{prompt.length}/3000</p>
              </div>
              <div className="flex items-center gap-4">
                <div className="space-y-1">
                  <Label className="text-muted-foreground text-xs">贴图质量</Label>
                  <Select
                    value={textureQuality}
                    onValueChange={(v) => v && setTextureQuality(v as typeof textureQuality)}
                  >
                    <SelectTrigger className="h-8 w-36 text-xs">
                      <span>
                        {textureQuality === "none"
                          ? "无贴图 ¥2.1+"
                          : textureQuality === "standard"
                            ? "标清贴图 ¥2.8+"
                            : "高清贴图 ¥3.5+"}
                      </span>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">无贴图 ¥2.1起</SelectItem>
                      <SelectItem value="standard">标清贴图 ¥2.8起</SelectItem>
                      <SelectItem value="detailed">高清贴图 ¥3.5起</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button
                  className="ml-auto gap-2"
                  disabled={isPending || !prompt.trim()}
                  onClick={() => submit()}
                >
                  <Layers className="size-4" />
                  {isPending ? "提交中..." : "生成 3D 模型"}
                </Button>
              </div>
            </Card>

            {/* 任务列表 */}
            {tasks.length > 0 && (
              <div className="space-y-3">
                <h2 className="font-medium text-muted-foreground text-sm">生成记录</h2>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                  {tasks.map((task) =>
                    task.status === "SUCCESS" && task.ossUrl ? (
                      <AssetCard
                        key={task.id}
                        asset={taskToAsset(task)}
                        onClick={() => {}}
                        onDelete={() => {}}
                        onRegenerate={() => {}}
                      />
                    ) : (
                      <Card
                        key={task.id}
                        className="flex aspect-square flex-col items-center justify-center gap-2 p-4 text-center"
                      >
                        {task.status === "FAIL" ? (
                          <>
                            <span className="text-2xl text-destructive">✕</span>
                            <p className="line-clamp-2 text-destructive text-xs">
                              {task.errorMsg ?? "生成失败"}
                            </p>
                          </>
                        ) : (
                          <>
                            <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                            <p className="line-clamp-2 text-muted-foreground text-xs">
                              {task.prompt}
                            </p>
                          </>
                        )}
                      </Card>
                    )
                  )}
                </div>
              </div>
            )}
          </TabsContent>

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
    </SceneLayout>
  )
}
