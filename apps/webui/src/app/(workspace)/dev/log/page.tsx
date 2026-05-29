/**
 * 开发日志面板（/workspace/dev/log）
 * 左侧任务编号列表 + 右侧 Markdown 时间线视图
 * @author AaronZZH & Kiro
 */
"use client"

import { useQuery } from "@tanstack/react-query"
import { CheckCircle2, Circle, Search } from "lucide-react"
import { useMemo, useState } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { request } from "@/lib/api/client"
import { cn } from "@/lib/utils/cn"
import type { DocTreeNode, Document } from "@/lib/types/document"

/** 从文档树中筛选 dev-log 文档 */
function collectDevLogs(nodes: DocTreeNode[]): DocTreeNode[] {
  const result: DocTreeNode[] = []
  for (const node of nodes) {
    if (!node.isDir && node.path.includes("dev-log")) {
      result.push(node)
    }
    if (node.children?.length) {
      result.push(...collectDevLogs(node.children))
    }
  }
  return result
}

/** 从文件名提取任务编号（如 AAF-024） */
function extractTaskId(path: string): string {
  const match = path.match(/AAF-\d+/i)
  return match ? match[0] : path.split("/").pop()?.replace(".md", "") ?? path
}

/** 解析 dev-log 内容中的条目 */
interface LogEntry {
  taskId: string
  date: string
  done: boolean
  content: string
}

function parseLogEntries(content: string): LogEntry[] {
  const entries: LogEntry[] = []
  const sections = content.split(/^## /m).filter(Boolean)

  for (const section of sections) {
    const lines = section.trim().split("\n")
    const title = lines[0] ?? ""
    const taskMatch = title.match(/#(\S+)/)
    const taskId = taskMatch ? `#${taskMatch[1]}` : title.slice(0, 30)
    const dateLine = lines.find((l) => l.includes("✅") || l.match(/\d{2}-\d{2}/))
    const date = dateLine?.match(/(\d{2}-\d{2})/)?.[1] ?? ""
    const done = section.includes("✅")
    const content = lines.slice(1).join("\n").trim()

    entries.push({ taskId, date, done, content })
  }
  return entries
}

export default function DevLogPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [search, setSearch] = useState("")

  const { data: tree, isLoading: treeLoading } = useQuery({
    queryKey: ["autodev-docs", "tree"],
    queryFn: () => request<DocTreeNode[]>("/autodev/docs/tree")
  })

  const { data: doc, isLoading: docLoading } = useQuery({
    queryKey: ["autodev-docs", selectedId],
    queryFn: () => request<Document>(`/autodev/docs/${selectedId}`),
    enabled: selectedId != null
  })

  const devLogs = useMemo(() => collectDevLogs(tree ?? []), [tree])

  const entries = useMemo(() => (doc ? parseLogEntries(doc.content) : []), [doc])

  /** 按日期分组 */
  const groupedEntries = useMemo(() => {
    const filtered = search
      ? entries.filter(
          (e) =>
            e.taskId.toLowerCase().includes(search.toLowerCase()) ||
            e.content.toLowerCase().includes(search.toLowerCase())
        )
      : entries
    const groups: Record<string, LogEntry[]> = {}
    for (const entry of filtered) {
      const key = entry.date || "未标注日期"
      if (!groups[key]) groups[key] = []
      groups[key].push(entry)
    }
    return groups
  }, [entries, search])

  return (
    <PageContainer>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="font-semibold text-lg">开发日志</h1>
      </div>

      <div className="flex gap-4" style={{ height: "calc(100vh - 12rem)" }}>
        {/* 左侧：任务编号列表 */}
        <Card className="w-64 shrink-0">
          <CardHeader className="border-b pb-3">
            <CardTitle className="text-sm">任务列表</CardTitle>
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
                  {devLogs.map((node) => (
                    <button
                      key={node.id ?? node.path}
                      type="button"
                      onClick={() => setSelectedId(node.id)}
                      className={cn(
                        "w-full rounded-md px-3 py-1.5 text-left text-sm transition-colors hover:bg-muted",
                        selectedId === node.id && "bg-muted font-medium"
                      )}
                    >
                      {extractTaskId(node.path)}
                    </button>
                  ))}
                  {devLogs.length === 0 && (
                    <p className="px-3 py-4 text-center text-muted-foreground text-xs">
                      暂无开发日志
                    </p>
                  )}
                </div>
              )}
            </ScrollArea>
          </CardContent>
        </Card>

        {/* 右侧：时间线视图 */}
        <div className="flex flex-1 flex-col overflow-hidden">
          {/* 搜索框 */}
          <div className="relative mb-3">
            <Search className="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="搜索日志内容..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>

          <ScrollArea className="flex-1">
            {!selectedId ? (
              <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
                选择左侧任务查看开发日志
              </div>
            ) : docLoading ? (
              <div className="space-y-4">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 w-full" />
                ))}
              </div>
            ) : (
              <div className="space-y-6">
                {Object.entries(groupedEntries).map(([date, items]) => (
                  <div key={date}>
                    <div className="mb-2 flex items-center gap-2">
                      <Badge variant="secondary">{date}</Badge>
                    </div>
                    <div className="space-y-2 border-l-2 border-muted pl-4">
                      {items.map((entry, idx) => (
                        <div key={idx} className="relative">
                          <div className="absolute -left-[1.35rem] top-1">
                            {entry.done ? (
                              <CheckCircle2 className="size-3.5 text-green-500" />
                            ) : (
                              <Circle className="size-3.5 text-muted-foreground" />
                            )}
                          </div>
                          <div className="rounded-md bg-muted/50 p-3">
                            <p className="mb-1 font-medium text-sm">{entry.taskId}</p>
                            <pre className="whitespace-pre-wrap text-muted-foreground text-xs leading-relaxed">
                              {entry.content}
                            </pre>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
                {Object.keys(groupedEntries).length === 0 && entries.length > 0 && (
                  <p className="text-center text-muted-foreground text-sm">无匹配结果</p>
                )}
                {entries.length === 0 && doc && (
                  <pre className="whitespace-pre-wrap font-mono text-sm leading-relaxed">
                    {doc.content}
                  </pre>
                )}
              </div>
            )}
          </ScrollArea>
        </div>
      </div>
    </PageContainer>
  )
}
