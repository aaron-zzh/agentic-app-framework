/**
 * /studio/templates 模板库
 *
 * 按 category 分 tab，GlassCard 网格，使用模板 → fork → 跳详情
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { FolderKanban } from "lucide-react"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  type UserProjectTemplateVO,
  useForkProjectTemplate,
  useProjectTemplates
} from "@/lib/queries/use-project-templates"

const CATEGORIES = [
  { key: "ALL", label: "全部" },
  { key: "CONTENT_OPS", label: "内容运营" },
  { key: "AIGC", label: "AIGC" },
  { key: "LIFE", label: "生活" },
  { key: "STUDY", label: "学习" },
  { key: "WORK", label: "工作" }
]

const TONE_MAP: Record<string, "violet" | "cyan"> = {
  CONTENT_OPS: "violet",
  AIGC: "cyan",
  LIFE: "cyan",
  STUDY: "violet",
  WORK: "cyan"
}

function TemplateSkeleton() {
  return (
    <div className="overflow-hidden rounded-2xl bg-card p-0">
      <Skeleton className="h-32 w-full rounded-none rounded-t-2xl" />
      <div className="space-y-2 p-4">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-full" />
      </div>
    </div>
  )
}

function TemplateCard({
  tpl,
  onUse
}: {
  tpl: UserProjectTemplateVO
  onUse: (tpl: UserProjectTemplateVO) => void
}) {
  const tone = TONE_MAP[tpl.category] ?? "violet"
  return (
    <GlassCard glow={tone} interactive={false} className="overflow-hidden">
      {/* 封面 */}
      <div className="relative flex h-32 items-center justify-center bg-foreground/[0.04]">
        {tpl.coverUrl ? (
          // biome-ignore lint/performance/noImgElement: 封面图
          <img src={tpl.coverUrl} alt={tpl.name} className="h-full w-full object-cover" />
        ) : (
          <FolderKanban className="size-10 text-muted-foreground/30" />
        )}
      </div>
      <div className="space-y-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <p className="font-medium text-sm leading-snug">{tpl.name}</p>
          <NeonChip tone={tone} size="sm">
            {tpl.category}
          </NeonChip>
        </div>
        {tpl.description && (
          <p className="line-clamp-2 text-muted-foreground text-xs leading-5">{tpl.description}</p>
        )}
        <div className="flex items-center justify-between pt-1">
          <span className="text-muted-foreground text-xs">{tpl.usageCount} 次使用</span>
          <GlowButton tone="primary" size="sm" onClick={() => onUse(tpl)}>
            使用此模板
          </GlowButton>
        </div>
      </div>
    </GlassCard>
  )
}

export default function StudioTemplatesPage() {
  const router = useRouter()
  const [activeCategory, setActiveCategory] = useState("ALL")
  const [forkTarget, setForkTarget] = useState<UserProjectTemplateVO | null>(null)
  const [projectName, setProjectName] = useState("")

  const queryParams =
    activeCategory === "ALL"
      ? { isOfficial: true, size: 50 }
      : { category: activeCategory, isOfficial: true, size: 50 }

  const { data: page, isLoading } = useProjectTemplates(queryParams)
  const templates = page?.list ?? []
  const fork = useForkProjectTemplate()

  const handleUse = (tpl: UserProjectTemplateVO) => {
    setForkTarget(tpl)
    setProjectName(tpl.name)
  }

  const handleFork = async () => {
    if (!forkTarget || !projectName.trim()) return
    const project = await fork.mutateAsync({ templateId: forkTarget.id, name: projectName })
    setForkTarget(null)
    router.push(`/studio/projects/${project.id}`)
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6">
      <header className="space-y-2">
        <h1 className="font-semibold text-xl">模板库</h1>
        <p className="text-muted-foreground text-sm">选择模板，一键创建项目</p>
      </header>

      <Tabs value={activeCategory} onValueChange={setActiveCategory}>
        <TabsList className="mb-4">
          {CATEGORIES.map((c) => (
            <TabsTrigger key={c.key} value={c.key}>
              {c.label}
            </TabsTrigger>
          ))}
        </TabsList>

        {CATEGORIES.map((c) => (
          <TabsContent key={c.key} value={c.key}>
            {isLoading ? (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {Array.from({ length: 8 }).map((_, i) => (
                  <TemplateSkeleton key={i} />
                ))}
              </div>
            ) : templates.length === 0 ? (
              <div className="py-20 text-center text-muted-foreground text-sm">暂无模板</div>
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {templates.map((tpl) => (
                  <TemplateCard key={tpl.id} tpl={tpl} onUse={handleUse} />
                ))}
              </div>
            )}
          </TabsContent>
        ))}
      </Tabs>

      {/* Fork Dialog */}
      <Dialog open={!!forkTarget} onOpenChange={(open) => !open && setForkTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>使用「{forkTarget?.name}」创建项目</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div className="space-y-1.5">
              <Label>项目名称</Label>
              <Input
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                placeholder="输入项目名称"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setForkTarget(null)}>
              取消
            </Button>
            <Button onClick={handleFork} disabled={!projectName.trim() || fork.isPending}>
              {fork.isPending ? "创建中…" : "创建项目"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
