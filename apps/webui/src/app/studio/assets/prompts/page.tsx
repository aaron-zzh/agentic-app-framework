/**
 * /studio/assets/prompts——我的提示词模板 CRUD
 * @author AaronZZH & Kiro
 */

"use client"

import { Pencil, Plus, Trash2 } from "lucide-react"
import { useState } from "react"
import { GlassCard, GlowButton } from "@/components/studio"
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
import { Textarea } from "@/components/ui/textarea"
import {
  type PromptTemplateVO,
  useCreatePromptTemplate,
  useDeletePromptTemplate,
  useMyPromptTemplates,
  useUpdatePromptTemplate
} from "@/lib/queries/use-prompt-templates"

interface EditDialogProps {
  open: boolean
  onClose: () => void
  initial?: PromptTemplateVO | null
}

function EditDialog({ open, onClose, initial }: EditDialogProps) {
  const [name, setName] = useState(initial?.name ?? "")
  const [prompt, setPrompt] = useState(initial?.prompt ?? "")
  const create = useCreatePromptTemplate()
  const update = useUpdatePromptTemplate()

  const handleSave = async () => {
    if (!name.trim() || !prompt.trim()) return
    if (initial) {
      await update.mutateAsync({ id: initial.id, name, prompt })
    } else {
      await create.mutateAsync({ name, category: "DEFAULT", prompt })
    }
    onClose()
  }

  const isPending = create.isPending || update.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{initial ? "编辑提示词" : "新建提示词"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <div className="space-y-1.5">
            <Label>名称</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="提示词名称"
            />
          </div>
          <div className="space-y-1.5">
            <Label>提示词内容</Label>
            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="输入提示词…"
              className="min-h-32"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={isPending || !name.trim() || !prompt.trim()}>
            {isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default function StudioAssetsPromptsPage() {
  const { data: page, isLoading } = useMyPromptTemplates({ size: 50 })
  const prompts = page?.list ?? []
  const deletePrompt = useDeletePromptTemplate()

  const [editTarget, setEditTarget] = useState<PromptTemplateVO | null | undefined>(undefined)
  // undefined = closed, null = create new, PromptTemplateVO = edit

  return (
    <div className="mx-auto max-w-4xl space-y-4 p-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="font-semibold text-xl">我的提示词</h1>
          <p className="mt-1 text-muted-foreground text-sm">管理你的常用提示词模板</p>
        </div>
        <GlowButton tone="primary" size="sm" onClick={() => setEditTarget(null)}>
          <Plus className="size-4" />
          新建
        </GlowButton>
      </header>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : prompts.length === 0 ? (
        <div className="py-20 text-center text-muted-foreground text-sm">
          还没有提示词，点击"新建"创建第一个吧
        </div>
      ) : (
        <div className="space-y-2">
          {prompts.map((p) => (
            <GlassCard key={p.id} glow="none" className="border border-foreground/6">
              <div className="flex items-start gap-3 p-4">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-sm">{p.name}</p>
                  <p className="mt-1 line-clamp-2 text-muted-foreground text-xs leading-5">
                    {p.prompt}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7"
                    onClick={() => setEditTarget(p)}
                  >
                    <Pencil className="size-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7 text-destructive hover:text-destructive"
                    onClick={() => deletePrompt.mutate(p.id)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </div>
              </div>
            </GlassCard>
          ))}
        </div>
      )}

      <EditDialog
        open={editTarget !== undefined}
        onClose={() => setEditTarget(undefined)}
        initial={editTarget}
      />
    </div>
  )
}
