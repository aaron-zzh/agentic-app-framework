/**
 * Content Studio 画布节点与参考图标注共用面板。
 *
 * 仅在显式打开后惰加载 tldraw；画布节点和临时标注使用独立持久化键。
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import type { ContentProjectObjectVO } from "@/lib/api/rest/content"

const CanvasPanel = dynamic(
  () => import("@/features/studio/projects/CanvasPanel").then((module) => module.CanvasPanel),
  { ssr: false, loading: () => <Skeleton className="size-full" /> }
)

export type ContentCanvasMode = "canvas" | "annotation"

export interface ContentCanvasSheetProps {
  open: boolean
  mode: ContentCanvasMode
  projectId: number
  object: ContentProjectObjectVO | null
  onOpenChange: (open: boolean) => void
}

export function ContentCanvasSheet({
  open,
  mode,
  projectId,
  object,
  onOpenChange
}: ContentCanvasSheetProps) {
  const isAnnotation = mode === "annotation"
  const title = object?.title || (isAnnotation ? "参考图标注" : "画布节点")
  const persistenceKey = object ? `content-studio:${projectId}:${mode}:${object.id}` : undefined

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[92vw] sm:max-w-[92vw]">
        <SheetHeader>
          <SheetTitle>{title}</SheetTitle>
          <SheetDescription>
            {isAnnotation
              ? "临时圈选、涂抹并标记局部修改意图；该会话不会创建项目对象。"
              : "自由排列参考图、手绘与批注；项目业务关系仍由项目图谱维护。"}
          </SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-hidden px-4 pb-4">
          {open && object ? (
            <CanvasPanel persistenceKey={persistenceKey} />
          ) : (
            <Skeleton className="size-full" />
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
