/**
 * 对象候选双选比较：文本、结构化内容、图片与视频统一并排展示。
 * @author AaronZZH & Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcObjectVersionComparisonItem } from "@/lib/api/rest/ai/aigc"
import { useAigcVersionComparison } from "@/lib/api/rest/ai/aigc"
import { useMediaVersionDetails } from "@/lib/api/rest/media/media-asset"

function CandidatePane({ item }: { item: AigcObjectVersionComparisonItem }) {
  const mediaQueries = useMediaVersionDetails(item.mediaVersionIds)
  const textValue = Object.values(item.content).find((value) => typeof value === "string")

  return (
    <section className="flex min-w-0 flex-col gap-3 rounded-xl border p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium">ObjectVersion #{item.objectVersionId}</p>
        <Badge variant={item.status === "adopted" ? "default" : "outline"}>{item.status}</Badge>
      </div>
      {typeof textValue === "string" && textValue.trim() ? (
        <div className="max-h-56 overflow-auto whitespace-pre-wrap rounded-lg border bg-muted/30 p-3 text-sm">{textValue}</div>
      ) : null}
      {Object.keys(item.content).length > 0 ? (
        <pre className="max-h-64 overflow-auto rounded-lg border bg-muted/30 p-3 text-xs">{JSON.stringify(item.content, null, 2)}</pre>
      ) : null}
      {mediaQueries.length > 0 ? (
        <div className="grid gap-2 sm:grid-cols-2">
          {mediaQueries.map((query, index) => {
            const media = query.data
            const mediaVersionId = item.mediaVersionIds[index]
            if (query.isLoading) return <Skeleton key={mediaVersionId} className="aspect-video" />
            if (!media) return <p key={mediaVersionId} className="text-destructive text-xs">MediaVersion #{mediaVersionId} 加载失败</p>
            return (
              <div key={mediaVersionId} className="overflow-hidden rounded-lg border bg-black/20">
                {media.mediaType === "VIDEO" ? (
                  <video src={media.currentVersion.url} controls preload="metadata" className="aspect-video w-full object-contain" />
                ) : (
                  // biome-ignore lint/performance/noImgElement: MediaVersion URL 可能为签名地址
                  <img src={media.currentVersion.url} alt={media.name} className="aspect-video w-full object-contain" />
                )}
                <p className="truncate px-2 py-1 text-xs">{media.name}</p>
              </div>
            )
          })}
        </div>
      ) : null}
      <div className="mt-auto flex flex-wrap gap-x-4 gap-y-1 text-muted-foreground text-xs">
        <span>来源 Run #{item.sourceExecutionRunId ?? "-"}</span>
        <span>费用 {item.creditCost ?? "-"}</span>
        <span>{new Date(item.createdTime).toLocaleString("zh-CN")}</span>
      </div>
      {item.documentVersionId ? <p className="text-muted-foreground text-xs">DocumentVersion #{item.documentVersionId}</p> : null}
    </section>
  )
}

interface CandidateComparisonDialogProps {
  open: boolean
  projectId: number
  objectId: number
  leftVersionId: number | null
  rightVersionId: number | null
  onOpenChange: (open: boolean) => void
}

export function CandidateComparisonDialog({ open, projectId, objectId, leftVersionId, rightVersionId, onOpenChange }: CandidateComparisonDialogProps) {
  const comparison = useAigcVersionComparison(projectId, objectId, leftVersionId, rightVersionId)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] max-w-6xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>候选版本比较</DialogTitle>
          <DialogDescription>双选比较不会移动采用指针；采用仍需单独确认。</DialogDescription>
        </DialogHeader>
        {comparison.isLoading ? (
          <div className="grid gap-4 md:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-96" /></div>
        ) : comparison.data ? (
          <div className="grid gap-4 md:grid-cols-2"><CandidatePane item={comparison.data.left} /><CandidatePane item={comparison.data.right} /></div>
        ) : (
          <p className="rounded-xl border border-dashed p-6 text-center text-muted-foreground">无法加载比较结果，请刷新权威版本后重试。</p>
        )}
      </DialogContent>
    </Dialog>
  )
}
