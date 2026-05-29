/**
 * 代码审查面板（/workspace/dev/review）
 * 左侧审查文档列表 + 右侧结构化展示（blocker/major/minor 统计）
 * @author AaronZZH & Kiro
 */
"use client"

import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, Ban, FileText, Info } from "lucide-react"
import { useMemo, useState } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { request } from "@/lib/api/client"
import type { DocTreeNode, Document } from "@/lib/types/document"
import { cn } from "@/lib/utils/cn"

/** 从文档树中筛选 review 文档 */
function collectReviewDocs(nodes: DocTreeNode[]): DocTreeNode[] {
  const result: DocTreeNode[] = []
  for (const node of nodes) {
    if (!node.isDir && node.path.includes("review")) {
      result.push(node)
    }
    if (node.children?.length) {
      result.push(...collectReviewDocs(node.children))
    }
  }
  return result
}

/** 审查问题 */
interface ReviewIssue {
  level: "blocker" | "major" | "minor"
  description: string
  file?: string
}

/** 解析 review.md 内容，提取问题列表 */
function parseReviewContent(content: string): ReviewIssue[] {
  const issues: ReviewIssue[] = []
  const lines = content.split("\n")

  let currentLevel: ReviewIssue["level"] | null = null

  for (const line of lines) {
    const lower = line.toLowerCase()
    if (lower.includes("blocker")) currentLevel = "blocker"
    else if (lower.includes("major")) currentLevel = "major"
    else if (lower.includes("minor")) currentLevel = "minor"

    // 匹配列表项（- 或 * 开头）
    const listMatch = line.match(/^[\s]*[-*]\s+(.+)/)
    if (listMatch && currentLevel) {
      const desc = listMatch[1]
      const fileMatch = desc.match(/`([^`]+\.\w+)`/)
      issues.push({
        level: currentLevel,
        description: desc,
        file: fileMatch?.[1]
      })
    }
  }
  return issues
}

const LEVEL_CONFIG = {
  blocker: { label: "Blocker", icon: Ban, color: "text-red-500", bg: "bg-red-500/10" },
  major: {
    label: "Major",
    icon: AlertTriangle,
    color: "text-orange-500",
    bg: "bg-orange-500/10"
  },
  minor: { label: "Minor", icon: Info, color: "text-blue-500", bg: "bg-blue-500/10" }
} as const

export default function DevReviewPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { data: tree, isLoading: treeLoading } = useQuery({
    queryKey: ["autodev-docs", "tree"],
    queryFn: () => request<DocTreeNode[]>("/autodev/docs/tree")
  })

  const { data: doc, isLoading: docLoading } = useQuery({
    queryKey: ["autodev-docs", selectedId],
    queryFn: () => request<Document>(`/autodev/docs/${selectedId}`),
    enabled: selectedId != null
  })

  const reviewDocs = useMemo(() => collectReviewDocs(tree ?? []), [tree])
  const issues = useMemo(() => (doc ? parseReviewContent(doc.content) : []), [doc])

  const stats = useMemo(
    () => ({
      blocker: issues.filter((i) => i.level === "blocker").length,
      major: issues.filter((i) => i.level === "major").length,
      minor: issues.filter((i) => i.level === "minor").length,
      total: issues.length
    }),
    [issues]
  )

  return (
    <PageContainer>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="font-semibold text-lg">代码审查</h1>
      </div>

      <div className="flex gap-4" style={{ height: "calc(100vh - 12rem)" }}>
        {/* 左侧：审查文档列表 */}
        <Card className="w-64 shrink-0">
          <CardHeader className="border-b pb-3">
            <CardTitle className="text-sm">审查文档</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <ScrollArea className="h-[calc(100vh-16rem)]">
              {treeLoading ? (
                <div className="space-y-2 p-3">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <Skeleton key={i} className="h-6 w-full" />
                  ))}
                </div>
              ) : (
                <div className="space-y-0.5 p-2">
                  {reviewDocs.map((node) => (
                    <button
                      key={node.id ?? node.path}
                      type="button"
                      onClick={() => setSelectedId(node.id)}
                      className={cn(
                        "w-full rounded-md px-3 py-1.5 text-left text-sm transition-colors hover:bg-muted",
                        selectedId === node.id && "bg-muted font-medium"
                      )}
                    >
                      <FileText className="mr-1.5 inline size-3.5" />
                      {node.name}
                    </button>
                  ))}
                  {reviewDocs.length === 0 && (
                    <p className="px-3 py-4 text-center text-muted-foreground text-xs">
                      暂无审查文档
                    </p>
                  )}
                </div>
              )}
            </ScrollArea>
          </CardContent>
        </Card>

        {/* 右侧：结构化展示 */}
        <div className="flex flex-1 flex-col overflow-hidden">
          {!selectedId ? (
            <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
              选择左侧文档查看审查详情
            </div>
          ) : docLoading ? (
            <div className="space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-20 w-full" />
              ))}
            </div>
          ) : (
            <ScrollArea className="flex-1">
              {/* 统计卡片 */}
              <div className="mb-4 grid grid-cols-4 gap-3">
                {(["blocker", "major", "minor"] as const).map((level) => {
                  const config = LEVEL_CONFIG[level]
                  const Icon = config.icon
                  return (
                    <Card key={level} size="sm">
                      <CardContent className="flex items-center gap-3 py-3">
                        <div className={cn("rounded-md p-2", config.bg)}>
                          <Icon className={cn("size-4", config.color)} />
                        </div>
                        <div>
                          <p className="text-muted-foreground text-xs">{config.label}</p>
                          <p className="font-semibold text-lg">{stats[level]}</p>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
                <Card size="sm">
                  <CardContent className="flex items-center gap-3 py-3">
                    <div className="rounded-md bg-muted p-2">
                      <FileText className="size-4 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-muted-foreground text-xs">总计</p>
                      <p className="font-semibold text-lg">{stats.total}</p>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* 问题列表 */}
              <Card>
                <CardHeader className="border-b pb-3">
                  <CardTitle className="text-sm">问题列表</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  {issues.length === 0 ? (
                    <p className="p-4 text-center text-muted-foreground text-sm">
                      未解析到结构化问题，显示原文
                    </p>
                  ) : (
                    <div className="divide-y">
                      {issues.map((issue, idx) => {
                        const config = LEVEL_CONFIG[issue.level]
                        return (
                          <div key={idx} className="flex items-start gap-3 px-4 py-3">
                            <Badge
                              variant={issue.level === "blocker" ? "destructive" : "secondary"}
                              className="mt-0.5 shrink-0"
                            >
                              {config.label}
                            </Badge>
                            <div className="min-w-0 flex-1">
                              <p className="text-sm">{issue.description}</p>
                              {issue.file && (
                                <p className="mt-0.5 font-mono text-muted-foreground text-xs">
                                  {issue.file}
                                </p>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* 原文 fallback */}
              {issues.length === 0 && doc && (
                <Card className="mt-4">
                  <CardContent className="p-4">
                    <pre className="whitespace-pre-wrap font-mono text-sm leading-relaxed">
                      {doc.content}
                    </pre>
                  </CardContent>
                </Card>
              )}
            </ScrollArea>
          )}
        </div>
      </div>
    </PageContainer>
  )
}
