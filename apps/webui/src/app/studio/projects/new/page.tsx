/**
 * /studio/projects/new——新建项目
 *
 * 步骤 1：选模板或空白项目
 * 步骤 2：填项目名 + 类型 + 描述
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useState } from "react"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { useCreateAigcProject } from "@/lib/queries/use-aigc-projects"
import {
  type UserProjectTemplateVO,
  useForkProjectTemplate,
  useProjectTemplates
} from "@/lib/queries/use-project-templates"
import { cn } from "@/lib/utils/index"

const PROJECT_TYPES = [
  { value: "IMAGE_POST", label: "图文" },
  { value: "SHORT_VIDEO", label: "短视频" },
  { value: "MIXED", label: "综合" },
  { value: "CONTENT_OPS", label: "内容运营" },
  { value: "LIFE", label: "生活" },
  { value: "STUDY", label: "学习" },
  { value: "WORK", label: "工作" }
]

export default function StudioProjectNewPage() {
  const router = useRouter()
  const [step, setStep] = useState<1 | 2>(1)
  const [selectedTemplate, setSelectedTemplate] = useState<UserProjectTemplateVO | null>(null)
  const [name, setName] = useState("")
  const [type, setType] = useState("MIXED")
  const [description, setDescription] = useState("")

  const { data: page, isLoading } = useProjectTemplates({ isOfficial: true, size: 20 })
  const templates = page?.list ?? []
  const createProject = useCreateAigcProject()
  const forkTemplate = useForkProjectTemplate()

  const handleNext = () => {
    if (selectedTemplate) {
      setName(selectedTemplate.name)
    }
    setStep(2)
  }

  const handleCreate = async () => {
    if (!name.trim()) return
    if (selectedTemplate) {
      const project = await forkTemplate.mutateAsync({
        templateId: selectedTemplate.id,
        name,
        description
      })
      router.push(`/studio/projects/${project.id}`)
    } else {
      const project = await createProject.mutateAsync({ name, type, description })
      router.push(`/studio/projects/${project.id}`)
    }
  }

  const isPending = createProject.isPending || forkTemplate.isPending

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <header className="space-y-2">
        <h1 className="font-semibold text-xl">新建项目</h1>
        <p className="text-muted-foreground text-sm">
          {step === 1 ? "选择模板或从空白开始" : "填写项目基本信息"}
        </p>
      </header>

      {step === 1 && (
        <div className="space-y-4">
          {/* 空白项目 */}
          <button
            type="button"
            onClick={() => {
              setSelectedTemplate(null)
              setStep(2)
            }}
            className={cn(
              "w-full rounded-2xl border-2 border-dashed p-5 text-left transition-colors",
              "border-foreground/[0.12] hover:border-violet-400/40 hover:bg-violet-400/[0.03]"
            )}
          >
            <p className="font-medium text-sm">空白项目</p>
            <p className="mt-1 text-muted-foreground text-xs">从零开始，自由创作</p>
          </button>

          <p className="text-muted-foreground text-xs">或选择模板快速开始：</p>

          {isLoading ? (
            <div className="grid grid-cols-2 gap-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-24 rounded-2xl" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {templates.map((tpl) => (
                <button
                  key={tpl.id}
                  type="button"
                  onClick={() => setSelectedTemplate(tpl)}
                  className={cn(
                    "rounded-2xl border p-4 text-left transition-all",
                    selectedTemplate?.id === tpl.id
                      ? "border-violet-400/40 bg-violet-400/6"
                      : "border-foreground/6 hover:border-foreground/12"
                  )}
                >
                  <p className="font-medium text-sm">{tpl.name}</p>
                  {tpl.description && (
                    <p className="mt-1 line-clamp-2 text-muted-foreground text-xs">
                      {tpl.description}
                    </p>
                  )}
                  <NeonChip tone="violet" size="sm" className="mt-2">
                    {tpl.category}
                  </NeonChip>
                </button>
              ))}
            </div>
          )}

          {selectedTemplate && (
            <div className="flex justify-end">
              <GlowButton tone="primary" size="sm" onClick={handleNext}>
                下一步
              </GlowButton>
            </div>
          )}
        </div>
      )}

      {step === 2 && (
        <GlassCard glow="violet">
          <div className="space-y-4 p-5">
            <div className="space-y-1.5">
              <Label>项目名称 *</Label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="给项目起个名字"
              />
            </div>

            {!selectedTemplate && (
              <div className="space-y-1.5">
                <Label>项目类型</Label>
                <Select value={type} onValueChange={(v) => setType(v ?? "MIXED")}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PROJECT_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="space-y-1.5">
              <Label>项目描述</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="描述项目的目标和内容（可选）"
                className="min-h-24"
              />
            </div>

            <div className="flex items-center justify-between">
              <Button variant="ghost" size="sm" onClick={() => setStep(1)}>
                ← 返回
              </Button>
              <GlowButton
                tone="primary"
                size="sm"
                onClick={handleCreate}
                disabled={!name.trim() || isPending}
              >
                {isPending ? "创建中…" : "创建项目"}
              </GlowButton>
            </div>
          </div>
        </GlassCard>
      )}
    </div>
  )
}
