/**
 * 知识库后台运维详情。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ArrowLeft,
  Database,
  ExternalLink,
  FileText,
  HardDrive,
  Layers,
  RefreshCw
} from "lucide-react"
import Link from "next/link"
import { use } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH1 } from "@/components/ui/typography"
import {
  useAdminKnowledge,
  useAdminKnowledgeDocuments,
  useAdminKnowledgeProjection,
  useAdminKnowledgeStats,
  useAdminRebuildKnowledgeProjection,
  useAdminRetryKnowledgeDocument
} from "@/lib/api/rest/admin"
import { notify } from "@/lib/notification"
import { useAuthStore } from "@/lib/store/auth-store"
import type { KnowledgeDocumentStatus } from "@/lib/types/knowledge"

const DOCUMENT_STATUS: Record<
  KnowledgeDocumentStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  0: { label: "待处理", variant: "outline" },
  1: { label: "处理中", variant: "secondary" },
  2: { label: "已完成", variant: "default" },
  3: { label: "失败", variant: "destructive" }
}

export default function AdminKnowledgeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: rawId } = use(params)
  const id = Number(rawId)
  const currentUserId = useAuthStore((state) => state.user?.id)
  const { data: knowledgeBase, isLoading } = useAdminKnowledge(id)
  const { data: stats } = useAdminKnowledgeStats(id)
  const { data: documentPage, isLoading: documentsLoading } = useAdminKnowledgeDocuments(id)
  const {
    data: projection,
    isLoading: projectionLoading,
    isError: projectionUnavailable
  } = useAdminKnowledgeProjection(id)
  const retryDocument = useAdminRetryKnowledgeDocument(id)
  const rebuildProjection = useAdminRebuildKnowledgeProjection(id)

  function handleRebuild() {
    const requestKey = `knowledge-projection-${id}-${globalThis.crypto.randomUUID()}`
    rebuildProjection.mutate(requestKey, {
      onSuccess: () => notify.success("图投影重建已提交")
    })
  }

  if (!Number.isSafeInteger(id) || id <= 0) {
    return (
      <PageContainer>
        <p className="text-destructive">知识库 ID 无效</p>
      </PageContainer>
    )
  }

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="mb-4 h-9 w-64" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={`maintenance-stat-${index}`} className="h-28" />
          ))}
        </div>
      </PageContainer>
    )
  }

  if (!knowledgeBase) return null

  const canOpenStudio =
    knowledgeBase.ownerId !== null && String(knowledgeBase.ownerId) === currentUserId
  const statCards = [
    { icon: FileText, label: "文档数", value: stats?.documentCount ?? 0 },
    { icon: Layers, label: "分块数", value: stats?.chunkCount ?? 0 },
    { icon: Database, label: "向量数", value: stats?.embeddingCount ?? 0 },
    { icon: HardDrive, label: "总大小", value: formatSize(stats?.totalSize ?? 0) }
  ]

  return (
    <PageContainer>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <Button
            nativeButton={false}
            variant="ghost"
            size="icon"
            aria-label="返回知识库运维列表"
            render={<Link href="/admin/knowledge" />}
          >
            <ArrowLeft />
          </Button>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <TypographyH1>{knowledgeBase.name}</TypographyH1>
              <Badge variant={knowledgeBase.status === 0 ? "default" : "secondary"}>
                {knowledgeBase.status === 0 ? "启用" : "禁用"}
              </Badge>
              <Badge variant="outline">{knowledgeBase.visibility}</Badge>
            </div>
            <p className="mt-1 break-all text-muted-foreground text-xs">{knowledgeBase.stableId}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {canOpenStudio ? (
            <Button
              nativeButton={false}
              variant="outline"
              render={<Link href={`/studio/knowledge/${id}`} />}
            >
              <ExternalLink data-icon="inline-start" />
              前台查看
            </Button>
          ) : null}
          <Button onClick={handleRebuild} disabled={rebuildProjection.isPending}>
            <RefreshCw
              data-icon="inline-start"
              className={rebuildProjection.isPending ? "animate-spin" : undefined}
            />
            {rebuildProjection.isPending ? "提交中..." : "重建图投影"}
          </Button>
        </div>
      </div>

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statCards.map((stat) => (
          <Card key={stat.label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="font-medium text-muted-foreground text-sm">
                {stat.label}
              </CardTitle>
              <stat.icon className="text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <p className="font-bold text-2xl">{stat.value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>归属与配置</CardTitle>
            <CardDescription>ADMIN_MAINTENANCE 仍受当前组织范围约束。</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Metadata label="Owner ID" value={knowledgeBase.ownerId} />
              <Metadata label="Org ID" value={knowledgeBase.orgId} />
              <Metadata label="Workspace ID" value={knowledgeBase.workspaceId} />
              <Metadata label="Scope Code" value={knowledgeBase.scopeCode} />
              <Metadata label="Embedding 模型" value={knowledgeBase.embeddingModel} />
              <Metadata
                label="分块配置"
                value={`${knowledgeBase.chunkStrategy} / ${knowledgeBase.chunkSize} / ${knowledgeBase.chunkOverlap}`}
              />
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Neo4j 投影 checkpoint</CardTitle>
            <CardDescription>Neo4j 是可重建投影，PostgreSQL 仍是唯一真理源。</CardDescription>
          </CardHeader>
          <CardContent>
            {projectionLoading ? (
              <Skeleton className="h-28" />
            ) : projectionUnavailable || !projection ? (
              <p className="text-muted-foreground text-sm">暂未建立投影 checkpoint</p>
            ) : (
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <Metadata label="状态" value={projection.status} />
                <Metadata label="就绪" value={projection.ready ? "是" : "否"} />
                <Metadata label="基线水位" value={projection.baseWatermark} />
                <Metadata label="期望水位" value={projection.desiredWatermark} />
                <Metadata label="已应用水位" value={projection.appliedWatermark} />
                <Metadata label="更新时间" value={projection.updatedAt} />
                {projection.errorMessage ? (
                  <div className="sm:col-span-2">
                    <dt className="text-muted-foreground">失败原因</dt>
                    <dd className="mt-1 text-destructive">{projection.errorMessage}</dd>
                  </div>
                ) : null}
              </dl>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>文档处理状态</CardTitle>
          <CardDescription>仅失败文档允许从后台重新提交处理。</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>文档</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>分块</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>失败原因</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documentsLoading ? (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    加载中...
                  </TableCell>
                </TableRow>
              ) : documentPage?.list.length ? (
                documentPage.list.map((document) => {
                  const status = DOCUMENT_STATUS[document.status]
                  return (
                    <TableRow key={document.id}>
                      <TableCell>
                        <p className="font-medium">{document.title}</p>
                        <p className="text-muted-foreground text-xs">ID: {document.id}</p>
                      </TableCell>
                      <TableCell className="max-w-56 truncate">
                        {document.sourceUri ?? document.sourceKey ?? "-"}
                      </TableCell>
                      <TableCell>{document.chunkCount}</TableCell>
                      <TableCell>
                        <Badge variant={status.variant}>{status.label}</Badge>
                      </TableCell>
                      <TableCell className="max-w-72 truncate text-destructive text-sm">
                        {document.errorMessage ?? "-"}
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm">
                        {new Date(document.updateTime).toLocaleString("zh-CN")}
                      </TableCell>
                      <TableCell className="text-right">
                        {document.status === 3 ? (
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={retryDocument.isPending}
                            onClick={() =>
                              retryDocument.mutate(document.id, {
                                onSuccess: () => notify.success("失败文档已重新提交")
                              })
                            }
                          >
                            <RefreshCw
                              data-icon="inline-start"
                              className={retryDocument.isPending ? "animate-spin" : undefined}
                            />
                            重试
                          </Button>
                        ) : (
                          <span className="text-muted-foreground text-xs">无需操作</span>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    暂无文档
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </PageContainer>
  )
}

function Metadata({ label, value }: { label: string; value: string | number | null }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all">{value ?? "-"}</dd>
    </div>
  )
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B"
  const units = ["B", "KB", "MB", "GB"]
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(1)} ${units[index]}`
}
