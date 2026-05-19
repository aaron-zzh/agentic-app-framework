/**
 * 知识库详情页——统计卡片 + 文档列表 + Tab 切换
 * @author AaronZZH & Kiro
 */

"use client"

import { use } from "react"
import { FileText, Database, Layers, HardDrive } from "lucide-react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1 } from "@/components/ui/typography"
import { useKnowledgeBase, useKnowledgeBaseStats, useKnowledgeDocuments } from "@/lib/queries/use-knowledge"
import { DocumentUpload } from "@/features/knowledge/components/DocumentUpload"
import { KnowledgeGraph } from "@/features/knowledge/components/KnowledgeGraph"
import { SearchTestPanel } from "@/features/knowledge/components/SearchTestPanel"
import { KnowledgeSettings } from "@/features/knowledge/components/KnowledgeSettings"

const STATUS_MAP: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
  pending: { label: "待处理", variant: "outline" },
  processing: { label: "处理中", variant: "secondary" },
  completed: { label: "已完成", variant: "default" },
  failed: { label: "失败", variant: "destructive" }
}

export default function KnowledgeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const { data: kb, isLoading } = useKnowledgeBase(id)
  const { data: stats } = useKnowledgeBaseStats(id)
  const { data: docsData } = useKnowledgeDocuments(id)

  const documents = docsData?.list ?? []

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="mb-4 h-8 w-48" />
        <div className="grid gap-4 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={`sk-${i}`} className="h-24" />
          ))}
        </div>
      </PageContainer>
    )
  }

  if (!kb) return null

  const statCards = [
    { icon: FileText, label: "文档数", value: stats?.documentCount ?? 0 },
    { icon: Layers, label: "分块数", value: stats?.chunkCount ?? 0 },
    { icon: Database, label: "向量数", value: stats?.vectorCount ?? 0 },
    { icon: HardDrive, label: "总大小", value: formatSize(stats?.totalSize ?? 0) }
  ]

  return (
    <PageContainer>
      <TypographyH1 className="mb-2 text-2xl">{kb.name}</TypographyH1>
      {kb.description && <p className="text-muted-foreground mb-6">{kb.description}</p>}

      {/* 统计卡片 */}
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statCards.map((s) => (
          <Card key={s.label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-muted-foreground text-sm font-medium">{s.label}</CardTitle>
              <s.icon className="text-muted-foreground size-4" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{s.value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Tab 区域 */}
      <Tabs defaultValue="documents">
        <TabsList>
          <TabsTrigger value="documents">文档</TabsTrigger>
          <TabsTrigger value="graph">知识图谱</TabsTrigger>
          <TabsTrigger value="search">检索测试</TabsTrigger>
          <TabsTrigger value="settings">设置</TabsTrigger>
        </TabsList>

        <TabsContent value="documents" className="mt-4 space-y-4">
          <DocumentUpload knowledgeBaseId={id} />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>文件名</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>大小</TableHead>
                <TableHead>分块数</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>上传时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => {
                const st = STATUS_MAP[doc.status] ?? STATUS_MAP.pending
                return (
                  <TableRow key={doc.id}>
                    <TableCell className="font-medium">{doc.name}</TableCell>
                    <TableCell>{doc.type}</TableCell>
                    <TableCell>{formatSize(doc.size)}</TableCell>
                    <TableCell>{doc.chunkCount}</TableCell>
                    <TableCell><Badge variant={st.variant}>{st.label}</Badge></TableCell>
                    <TableCell>{new Date(doc.createdAt).toLocaleDateString()}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TabsContent>

        <TabsContent value="graph" className="mt-4">
          <KnowledgeGraph knowledgeBaseId={id} />
        </TabsContent>

        <TabsContent value="search" className="mt-4">
          <SearchTestPanel knowledgeBaseId={id} />
        </TabsContent>

        <TabsContent value="settings" className="mt-4">
          <KnowledgeSettings knowledgeBase={kb} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  )
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B"
  const units = ["B", "KB", "MB", "GB"]
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / 1024 ** i).toFixed(1)} ${units[i]}`
}
