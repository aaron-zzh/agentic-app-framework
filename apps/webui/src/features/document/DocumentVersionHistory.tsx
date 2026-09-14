"use client"

import { GitCompareArrows, History, RotateCcw } from "lucide-react"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  useDocumentDiff,
  useDocumentVersions,
  useRestoreDocumentVersion
} from "@/lib/api/rest/system/document"
import type { DocumentVersion } from "@/lib/types/document"
import { DocumentDiffViewer } from "./DocumentDiffViewer"

interface DocumentVersionHistoryProps {
  documentId: number
  currentVersionId: number
  publishedVersionId?: number | null
}

const SOURCE_LABELS: Record<DocumentVersion["sourceType"], string> = {
  MANUAL: "手工",
  AIGC: "AIGC",
  IMPORT: "导入",
  RESTORE: "恢复"
}

/** 展示不可变版本时间线，并按完整快照请求服务端逐行 Diff。 */
export function DocumentVersionHistory({
  documentId,
  currentVersionId,
  publishedVersionId
}: DocumentVersionHistoryProps) {
  const versionsQuery = useDocumentVersions(documentId)
  const versions = versionsQuery.data ?? []
  const [baseVersionId, setBaseVersionId] = useState<number | null>(null)
  const [targetVersionId, setTargetVersionId] = useState<number | null>(null)
  const [restoreVersion, setRestoreVersion] = useState<DocumentVersion | null>(null)
  const diffQuery = useDocumentDiff(documentId, baseVersionId, targetVersionId)
  const sameVersion = baseVersionId !== null && baseVersionId === targetVersionId
  const restore = useRestoreDocumentVersion()

  useEffect(() => {
    if (versions.length === 0) return
    const current = versions.find((version) => version.id === currentVersionId) ?? versions[0]
    const base =
      versions.find((version) => version.id === current.parentVersionId) ??
      versions.find((version) => version.id !== current.id)
    setTargetVersionId((value) =>
      value !== null && versions.some((version) => version.id === value) ? value : current.id
    )
    setBaseVersionId((value) =>
      value !== null && versions.some((version) => version.id === value)
        ? value
        : (base?.id ?? null)
    )
  }, [currentVersionId, versions])

  function confirmRestore() {
    if (!restoreVersion) return
    restore.mutate(
      {
        id: documentId,
        sourceVersionId: restoreVersion.id,
        expectedCurrentVersionId: currentVersionId,
        changeSummary: `恢复 v${restoreVersion.versionNo}`
      },
      {
        onSuccess: () => {
          setRestoreVersion(null)
          toast.success(`已从 v${restoreVersion.versionNo} 创建新的当前版本`)
        },
        onError: (error) =>
          toast.error(`恢复失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  if (versionsQuery.isLoading) {
    return (
      <div className="space-y-3 p-6">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    )
  }

  if (versions.length === 0) {
    return <div className="p-8 text-center text-muted-foreground">暂无版本历史</div>
  }

  return (
    <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[300px_minmax(0,1fr)]">
      <aside className="min-h-0 overflow-auto border-b p-3 lg:border-r lg:border-b-0">
        <div className="mb-3 flex items-center gap-2 px-1 font-medium text-sm">
          <History className="size-4" />
          版本时间线
        </div>
        <div className="space-y-2">
          {versions.map((version) => (
            <div key={version.id} className="rounded-lg border p-3 text-sm">
              <div className="flex items-center gap-2">
                <span className="font-medium">v{version.versionNo}</span>
                {version.id === currentVersionId ? <Badge>当前</Badge> : null}
                {version.id === publishedVersionId ? (
                  <Badge variant="secondary">已发布</Badge>
                ) : null}
                <Badge variant="outline">{SOURCE_LABELS[version.sourceType]}</Badge>
              </div>
              <p className="mt-2 line-clamp-2 text-muted-foreground text-xs">
                {version.changeSummary || "未填写修改说明"}
              </p>
              <p className="mt-2 text-muted-foreground text-xs">
                {version.createdByType || "SYSTEM"} ·{" "}
                {version.createdBy ? `#${version.createdBy}` : "系统"} ·{" "}
                {new Date(version.createdTime).toLocaleString("zh-CN")}
              </p>
              {version.id !== currentVersionId ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  className="mt-2"
                  onClick={() => setRestoreVersion(version)}
                >
                  <RotateCcw />
                  恢复此版本
                </Button>
              ) : null}
            </div>
          ))}
        </div>
      </aside>

      <section className="flex min-h-0 flex-col">
        <div className="flex flex-wrap items-end gap-3 border-b p-3">
          <div className="min-w-44 flex-1 space-y-1">
            <span className="text-muted-foreground text-xs">基准版本</span>
            <Select
              value={baseVersionId === null ? "" : String(baseVersionId)}
              onValueChange={(value) => setBaseVersionId(value ? Number(value) : null)}
            >
              <SelectTrigger aria-label="选择基准版本">
                <SelectValue placeholder="选择版本" />
              </SelectTrigger>
              <SelectContent>
                {versions.map((version) => (
                  <SelectItem key={version.id} value={String(version.id)}>
                    v{version.versionNo} · {version.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <GitCompareArrows className="mb-2 size-4 text-muted-foreground" />
          <div className="min-w-44 flex-1 space-y-1">
            <span className="text-muted-foreground text-xs">目标版本</span>
            <Select
              value={targetVersionId === null ? "" : String(targetVersionId)}
              onValueChange={(value) => setTargetVersionId(value ? Number(value) : null)}
            >
              <SelectTrigger aria-label="选择目标版本">
                <SelectValue placeholder="选择版本" />
              </SelectTrigger>
              <SelectContent>
                {versions.map((version) => (
                  <SelectItem key={version.id} value={String(version.id)}>
                    v{version.versionNo} · {version.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="min-h-0 flex-1 p-3">
          <DocumentDiffViewer
            diff={sameVersion ? undefined : diffQuery.data}
            loading={!sameVersion && diffQuery.isLoading}
            error={!sameVersion && diffQuery.isError}
            emptyMessage={sameVersion ? "请选择两个不同版本" : "两个版本正文相同"}
            className="h-full min-h-60"
          />
        </div>
      </section>

      <ConfirmDialog
        open={restoreVersion !== null}
        onOpenChange={(open) => {
          if (!open) setRestoreVersion(null)
        }}
        title={`恢复 v${restoreVersion?.versionNo ?? ""}`}
        description="恢复不会改写历史版本，而会复制该版本正文并创建一个新的当前版本。"
        confirmText={restore.isPending ? "恢复中..." : "确认恢复"}
        onConfirm={confirmRestore}
      />
    </div>
  )
}
