/**
 * 新建 AIGC 创作项目
 * @author AaronZZH & Kiro
 */

"use client"

import { Image, Layers, PenLine, Video } from "lucide-react"
import { useRouter } from "next/navigation"
import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { RichTextEditor } from "@/features/rich-text-editor"
import { useCreateAigcProject } from "@/lib/queries/use-aigc-projects"
import { cn } from "@/lib/utils/cn"

const PROJECT_TYPES = [
  {
    value: "IMAGE_POST",
    label: "图像生成",
    desc: "AI 文生图、参考图",
    icon: Image,
    route: "image"
  },
  {
    value: "SHORT_VIDEO",
    label: "视频生成",
    desc: "文生视频、图生视频",
    icon: Video,
    route: "video"
  },
  {
    value: "VIDEO_DRAMA",
    label: "视频剧集",
    desc: "分镜 + 时间轴编排",
    icon: Video,
    route: "video"
  },
  { value: "MIXED", label: "综合项目", desc: "图像 + 视频 + 文案", icon: Layers, route: "image" }
] as const

export default function AigcNewProjectPage() {
  const router = useRouter()
  const [name, setName] = useState("")
  const [type, setType] = useState<string>("IMAGE_POST")
  const [prompt, setPrompt] = useState("")
  const descriptionRef = useRef("")
  const { mutate: createProject, isPending } = useCreateAigcProject()

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    createProject(
      {
        name: name.trim(),
        type,
        prompt: prompt.trim() || undefined,
        description: descriptionRef.current || undefined
      },
      {
        onSuccess: (project) => {
          const selected = PROJECT_TYPES.find((t) => t.value === type)
          router.push(`/aigc/${project.id}/${selected?.route ?? "image"}`)
        }
      }
    )
  }

  return (
    <div className="flex min-h-full justify-center p-6 pb-10">
      <div className="w-full max-w-4xl">
        <h1 className="mb-8 font-bold text-2xl">新建创作项目</h1>

        <form
          onSubmit={handleSubmit}
          className="space-y-8"
          onKeyDown={(e) => {
            if (e.key === "Enter" && e.nativeEvent.isComposing) e.preventDefault()
          }}
        >
          {/* 项目类型 — 大屏一行四列 */}
          <div className="space-y-3">
            <Label>项目类型</Label>
            <div className="grid grid-cols-4 gap-4">
              {PROJECT_TYPES.map(({ value, label, desc, icon: Icon }) => (
                <Card
                  key={value}
                  className={cn(
                    "cursor-pointer p-5 transition-colors hover:border-primary",
                    type === value && "border-primary bg-primary/5"
                  )}
                  onClick={() => setType(value)}
                >
                  <div className="flex flex-col items-center gap-3 text-center">
                    <Icon
                      className={cn(
                        "size-8",
                        type === value ? "text-primary" : "text-muted-foreground"
                      )}
                    />
                    <div>
                      <p className="font-semibold text-sm">{label}</p>
                      <p className="mt-1 text-muted-foreground text-xs">{desc}</p>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          </div>

          {/* 项目名称 */}
          <div className="space-y-2">
            <Label htmlFor="name">项目名称</Label>
            <Input
              id="name"
              placeholder="给项目起个名字..."
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              required
            />
          </div>

          {/* 提示词 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="prompt">提示词</Label>
              <PromptTemplateDialog
                type={type === "SHORT_VIDEO" || type === "VIDEO_DRAMA" ? "VIDEO" : "IMAGE"}
                onSelect={(p) => setPrompt(p)}
                scope="PROJECT"
              />
            </div>
            <Textarea
              id="prompt"
              placeholder="描述你想要创作的内容，AI 将根据此提示词生成素材..."
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              className="min-h-[120px] resize-y"
            />
          </div>

          {/* 项目简介 — 富文本 */}
          <div className="space-y-2">
            <Label>项目简介</Label>
            <RichTextEditor
              value=""
              onChange={(v) => {
                descriptionRef.current = v
              }}
              preset="richField"
              mode="markdown"
              minHeight={200}
              placeholder="添加项目简介，支持富文本格式..."
            />
          </div>

          {/* 操作按钮 */}
          <div className="flex gap-3">
            <Button
              type="button"
              variant="outline"
              className="flex-1"
              onClick={() => router.back()}
            >
              取消
            </Button>
            <Button type="submit" className="flex-1" disabled={isPending}>
              <PenLine className="mr-2 size-4" />
              {isPending ? "创建中..." : "开始创作"}
            </Button>
          </div>
        </form>
        <div className="h-10" />
      </div>
    </div>
  )
}
