"use client"

import { Video } from "lucide-react"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { type Scene, SceneEditor } from "@/features/digital-human/video"

interface VideoGenerationDialogProps {
  digitalHumanId: string
  digitalHumanName: string
  /** 触发按钮元素，通过 render prop 渲染为 Dialog 触发器 */
  trigger?: React.ReactElement
}

export function VideoGenerationDialog({
  digitalHumanId,
  digitalHumanName,
  trigger
}: VideoGenerationDialogProps) {
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<"simple" | "advanced">("simple")
  const [loading, setLoading] = useState(false)

  // 简单模式
  const [scriptContent, setScriptContent] = useState("")
  const [aspectRatio, setAspectRatio] = useState("16:9")

  // 高级模式
  const [scenes, setScenes] = useState<Scene[]>([
    {
      id: "scene-1",
      order: 1,
      title: "场景 1",
      content: "",
      duration: 10
    }
  ])

  const handleSimpleGenerate = async () => {
    if (!scriptContent.trim()) {
      toast.error("请输入视频脚本")
      return
    }

    try {
      setLoading(true)

      // 估算时长（简单按字数估算，假设每秒3个字）
      const estimatedDuration = Math.ceil(scriptContent.length / 3)

      // 创建AIGC项目
      const response = await fetch("/api/aigc/projects", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: `${digitalHumanName} - 视频生成`,
          blueprintCode: "digital-human-marketing-video",
          initialSlots: {
            script: {
              content: scriptContent,
              metadata: {
                digitalHumanId,
                aspectRatio,
                estimatedDuration
              }
            }
          }
        })
      })

      if (!response.ok) throw new Error("创建项目失败")

      const project = await response.json()
      toast.success("视频生成任务已创建")
      setOpen(false)

      // 跳转到项目详情页
      router.push(`/aigc/projects/${project.data.id}`)
    } catch {
      toast.error("创建视频生成任务失败")
    } finally {
      setLoading(false)
    }
  }

  const handleAdvancedGenerate = async () => {
    if (scenes.length === 0) {
      toast.error("请至少添加一个场景")
      return
    }

    if (scenes.some((s) => !s.content.trim())) {
      toast.error("请填写所有场景的内容")
      return
    }

    try {
      setLoading(true)

      const totalDuration = scenes.reduce((sum, s) => sum + s.duration, 0)
      const fullScript = scenes.map((s) => s.content).join(" ")

      // 创建AIGC项目
      const response = await fetch("/api/aigc/projects", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: `${digitalHumanName} - 多场景视频`,
          blueprintCode: "digital-human-marketing-video",
          initialSlots: {
            script: {
              content: fullScript,
              metadata: {
                digitalHumanId,
                aspectRatio,
                estimatedDuration: totalDuration,
                scenes: scenes.map((s) => ({
                  order: s.order,
                  title: s.title,
                  content: s.content,
                  duration: s.duration,
                  sceneDescription: s.sceneDescription
                }))
              }
            }
          }
        })
      })

      if (!response.ok) throw new Error("创建项目失败")

      const project = await response.json()
      toast.success("多场景视频生成任务已创建")
      setOpen(false)

      // 跳转到项目详情页
      router.push(`/aigc/projects/${project.data.id}`)
    } catch {
      toast.error("创建视频生成任务失败")
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          trigger ?? (
            <Button>
              <Video className="mr-2 size-4" />
              生成视频
            </Button>
          )
        }
      />
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>生成数字人视频</DialogTitle>
          <DialogDescription>为 {digitalHumanName} 创建视频内容</DialogDescription>
        </DialogHeader>

        <Tabs value={mode} onValueChange={(v) => setMode(v as "simple" | "advanced")}>
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="simple">简单模式</TabsTrigger>
            <TabsTrigger value="advanced">高级模式</TabsTrigger>
          </TabsList>

          {/* 简单模式 */}
          <TabsContent value="simple" className="space-y-4">
            <div>
              <Label htmlFor="script">视频脚本</Label>
              <textarea
                id="script"
                className="mt-1.5 min-h-[200px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                placeholder="输入数字人播报的视频脚本内容..."
                value={scriptContent}
                onChange={(e) => setScriptContent(e.target.value)}
              />
              <p className="mt-1.5 text-muted-foreground text-xs">
                预计时长: {Math.ceil(scriptContent.length / 3)} 秒 (按每秒3字估算)
              </p>
            </div>

            <div>
              <Label htmlFor="ratio">视频比例</Label>
              <Select value={aspectRatio} onValueChange={(v) => setAspectRatio(v ?? "16:9")}>
                <SelectTrigger id="ratio" className="mt-1.5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="16:9">16:9 (横屏)</SelectItem>
                  <SelectItem value="9:16">9:16 (竖屏)</SelectItem>
                  <SelectItem value="1:1">1:1 (方形)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </TabsContent>

          {/* 高级模式 */}
          <TabsContent value="advanced" className="space-y-4">
            <div>
              <Label htmlFor="ratio-advanced">视频比例</Label>
              <Select value={aspectRatio} onValueChange={(v) => setAspectRatio(v ?? "16:9")}>
                <SelectTrigger id="ratio-advanced" className="mt-1.5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="16:9">16:9 (横屏)</SelectItem>
                  <SelectItem value="9:16">9:16 (竖屏)</SelectItem>
                  <SelectItem value="1:1">1:1 (方形)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label>场景编辑</Label>
              <div className="mt-1.5">
                <SceneEditor value={scenes} onChange={setScenes} />
              </div>
            </div>
          </TabsContent>
        </Tabs>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            取消
          </Button>
          <Button
            onClick={mode === "simple" ? handleSimpleGenerate : handleAdvancedGenerate}
            disabled={loading}
          >
            {loading ? "创建中..." : "开始生成"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
