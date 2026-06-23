/**
 * SaveToProjectDialog——选择项目后一键保存素材
 *
 * 用于 GenerationResultCard 的"→保存到项目"按钮弹出的 Dialog。
 * 项目列表为空时引导创建。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { FolderPlus, Loader2, Plus } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { toast } from "sonner"
import { GlowButton } from "@/components/studio/GlowButton"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { useAigcProjects } from "@/lib/queries/use-aigc-projects"
import {
  type SaveFromGenerationParams,
  useSaveToAssetLibrary
} from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils/index"

interface SaveToProjectDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 要保存的素材参数（不含 projectId） */
  asset: Omit<SaveFromGenerationParams, "projectId">
}

export function SaveToProjectDialog({ open, onOpenChange, asset }: SaveToProjectDialogProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const { data, isLoading } = useAigcProjects({ page: 1, size: 30 })
  const saveToAsset = useSaveToAssetLibrary()
  const projects = data?.list ?? []

  const handleSave = async () => {
    if (!selectedId) return
    try {
      await saveToAsset.mutateAsync({ ...asset, projectId: selectedId })
      toast.success("已保存到项目")
      onOpenChange(false)
      setSelectedId(null)
    } catch {
      toast.error("保存失败，请重试")
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>保存到项目</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : projects.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <FolderPlus className="size-10 text-muted-foreground/40" />
            <p className="text-muted-foreground text-sm">还没有项目，创建第一个吧</p>
            <Link href="/studio/projects/new" onClick={() => onOpenChange(false)}>
              <GlowButton tone="violet" size="sm">
                <Plus className="size-4" />
                创建项目
              </GlowButton>
            </Link>
          </div>
        ) : (
          <ScrollArea className="max-h-60">
            <div className="space-y-1 pr-2">
              {projects.map((project) => (
                <div
                  key={project.id}
                  role="option"
                  aria-selected={selectedId === project.id}
                  onClick={() => setSelectedId(project.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") setSelectedId(project.id)
                  }}
                  tabIndex={0}
                  className={cn(
                    "flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors",
                    selectedId === project.id
                      ? "bg-primary/10 text-primary ring-1 ring-primary/30"
                      : "hover:bg-foreground/[0.04]"
                  )}
                >
                  <span className="flex-1 truncate">{project.name}</span>
                  <span className="text-muted-foreground text-xs">{project.type}</span>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}

        {projects.length > 0 && (
          <DialogFooter className="gap-2">
            <Button variant="ghost" size="sm" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <GlowButton
              tone="violet"
              size="sm"
              disabled={!selectedId || saveToAsset.isPending}
              onClick={handleSave}
            >
              {saveToAsset.isPending && <Loader2 className="size-4 animate-spin" />}
              保存
            </GlowButton>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
