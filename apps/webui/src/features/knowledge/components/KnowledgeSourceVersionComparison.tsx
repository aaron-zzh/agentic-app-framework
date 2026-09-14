/**
 * 知识文档精确来源版本定位与比较面板。
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { DocumentDiffViewer } from "@/features/document/DocumentDiffViewer"
import { useDocumentDiff, useDocumentVersions } from "@/lib/api/rest/system/document"
import type { DocumentVersion } from "@/lib/types/document"

interface KnowledgeSourceVersionComparisonProps {
  documentId: number
  sourceVersionId: number
}

const SOURCE_LABELS: Record<DocumentVersion["sourceType"], string> = {
  MANUAL: "手工",
  AIGC: "AIGC",
  IMPORT: "导入",
  RESTORE: "恢复"
}

/** 固定知识 ingest 的来源版本，并允许选择同一文档的历史版本进行比较。 */
export function KnowledgeSourceVersionComparison({
  documentId,
  sourceVersionId
}: KnowledgeSourceVersionComparisonProps) {
  const versionsQuery = useDocumentVersions(documentId)
  const versions = versionsQuery.data ?? []
  const sourceVersion = versions.find((version) => version.id === sourceVersionId)
  const [baseVersionId, setBaseVersionId] = useState<number | null>(null)
  const sameVersion = baseVersionId === null || baseVersionId === sourceVersionId
  const diffQuery = useDocumentDiff(documentId, baseVersionId, sourceVersionId)

  useEffect(() => {
    const availableVersions = versionsQuery.data
    if (!availableVersions) return

    const exactSource = availableVersions.find((version) => version.id === sourceVersionId)
    setBaseVersionId((current) => {
      if (
        current !== null &&
        current !== sourceVersionId &&
        availableVersions.some((version) => version.id === current)
      ) {
        return current
      }
      if (
        exactSource?.parentVersionId &&
        availableVersions.some((version) => version.id === exactSource.parentVersionId)
      ) {
        return exactSource.parentVersionId
      }
      return availableVersions.find((version) => version.id !== sourceVersionId)?.id ?? null
    })
  }, [sourceVersionId, versionsQuery.data])

  if (versionsQuery.isLoading) {
    return <Skeleton className="h-72 w-full" />
  }

  if (versionsQuery.isError) {
    return (
      <Alert variant="destructive">
        <AlertTitle>来源版本历史加载失败</AlertTitle>
        <AlertDescription>无法定位知识内容使用的 DocumentVersion。</AlertDescription>
      </Alert>
    )
  }

  if (!sourceVersion) {
    return (
      <Alert variant="destructive">
        <AlertTitle>精确来源版本不可用</AlertTitle>
        <AlertDescription>
          Document #{documentId} 的版本历史中未找到 Version #{sourceVersionId}。
        </AlertDescription>
      </Alert>
    )
  }

  const baseVersion = versions.find((version) => version.id === baseVersionId)

  return (
    <section className="flex flex-col gap-3 rounded-lg border bg-background p-3">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-medium text-sm">来源版本历史</h3>
            <Badge>知识来源 v{sourceVersion.versionNo}</Badge>
            <Badge variant="outline">Version #{sourceVersion.id}</Badge>
          </div>
          <p className="text-muted-foreground text-xs">
            知识内容固定来自 Document #{documentId}，后续文档更新不会改变本次来源。
          </p>
        </div>
        <Button
          nativeButton={false}
          render={<Link href={`/docs/${documentId}/edit`} />}
          variant="outline"
          size="xs"
        >
          打开文档
        </Button>
      </header>

      <dl className="grid gap-2 rounded-md bg-muted/30 p-3 text-xs sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">来源类型</dt>
          <dd>{SOURCE_LABELS[sourceVersion.sourceType]}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">正文格式</dt>
          <dd>{sourceVersion.format}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">创建时间</dt>
          <dd>{new Date(sourceVersion.createdTime).toLocaleString("zh-CN")}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Checksum</dt>
          <dd className="truncate font-mono" title={sourceVersion.checksum}>
            {sourceVersion.checksum}
          </dd>
        </div>
      </dl>

      <div className="grid min-h-0 gap-3 lg:grid-cols-[220px_minmax(0,1fr)]">
        <fieldset className="m-0 flex max-h-80 min-w-0 flex-col gap-1 overflow-auto border-0 p-0">
          <legend className="sr-only">来源文档版本历史</legend>
          {versions.map((version) => {
            const isSource = version.id === sourceVersionId
            const isBase = version.id === baseVersionId
            return (
              <Button
                key={version.id}
                type="button"
                variant={isBase ? "secondary" : "ghost"}
                className="h-auto w-full justify-start whitespace-normal px-2 py-2 text-left"
                aria-pressed={isBase}
                onClick={() => setBaseVersionId(version.id)}
              >
                <span className="flex min-w-0 flex-1 flex-col items-start gap-1">
                  <span className="flex w-full items-center gap-1.5">
                    <span className="font-medium">v{version.versionNo}</span>
                    {isSource ? <Badge>来源</Badge> : null}
                    {isBase ? <Badge variant="outline">对比</Badge> : null}
                  </span>
                  <span className="line-clamp-1 text-muted-foreground text-xs">
                    {version.changeSummary || version.title}
                  </span>
                </span>
              </Button>
            )
          })}
        </fieldset>

        <div className="flex min-h-0 flex-col gap-2">
          <p className="text-muted-foreground text-xs">
            {baseVersion
              ? `比较 v${baseVersion.versionNo} 与知识来源 v${sourceVersion.versionNo}`
              : `知识来源 v${sourceVersion.versionNo} 暂无其他版本可比较`}
          </p>
          <DocumentDiffViewer
            diff={sameVersion ? undefined : diffQuery.data}
            loading={!sameVersion && diffQuery.isLoading}
            error={!sameVersion && diffQuery.isError}
            emptyMessage={sameVersion ? "请选择其他历史版本与知识来源比较" : "两个版本正文相同"}
            className="h-72"
          />
        </div>
      </div>
    </section>
  )
}
