/**
 * DocumentVersion 逐行差异共享查看器。
 * 统一承载 unified/split 布局、hunk、行号和加载/错误/空状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { Loader2 } from "lucide-react"
import { useState } from "react"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { DocumentDiff, DocumentDiffLine } from "@/lib/types/document"
import { cn } from "@/lib/utils"

export type DocumentDiffLayout = "unified" | "split"

interface DocumentDiffViewerProps {
  diff?: DocumentDiff
  loading?: boolean
  error?: boolean
  emptyMessage?: string
  className?: string
  defaultLayout?: DocumentDiffLayout
}

function lineClass(type: DocumentDiffLine["type"]) {
  if (type === "ADD") return "bg-emerald-500/10 text-emerald-800 dark:text-emerald-300"
  if (type === "DELETE") return "bg-red-500/10 text-red-800 dark:text-red-300"
  return "text-muted-foreground"
}

function LineNumber({ value }: { value: number | null }) {
  return (
    <span className="w-10 shrink-0 select-none text-right text-muted-foreground">
      {value ?? ""}
    </span>
  )
}

function UnifiedLine({ line }: { line: DocumentDiffLine }) {
  return (
    <div className={cn("flex gap-3 px-2 py-0.5", lineClass(line.type))}>
      <LineNumber value={line.oldLine} />
      <LineNumber value={line.newLine} />
      <span className="w-3 shrink-0 select-none">
        {line.type === "ADD" ? "+" : line.type === "DELETE" ? "-" : " "}
      </span>
      <pre className="min-w-0 flex-1 whitespace-pre-wrap break-words">{line.content || " "}</pre>
    </div>
  )
}

function SplitLine({ line }: { line: DocumentDiffLine }) {
  return (
    <div className="grid grid-cols-2 border-t first:border-t-0">
      <div
        className={cn(
          "flex min-w-0 gap-2 border-r px-2 py-0.5",
          line.type !== "ADD" && lineClass(line.type)
        )}
      >
        <LineNumber value={line.type === "ADD" ? null : line.oldLine} />
        <pre className="min-w-0 whitespace-pre-wrap break-words">
          {line.type === "ADD" ? " " : line.content || " "}
        </pre>
      </div>
      <div
        className={cn(
          "flex min-w-0 gap-2 px-2 py-0.5",
          line.type !== "DELETE" && lineClass(line.type)
        )}
      >
        <LineNumber value={line.type === "DELETE" ? null : line.newLine} />
        <pre className="min-w-0 whitespace-pre-wrap break-words">
          {line.type === "DELETE" ? " " : line.content || " "}
        </pre>
      </div>
    </div>
  )
}

export function DocumentDiffViewer({
  diff,
  loading = false,
  error = false,
  emptyMessage = "两个版本正文相同",
  className,
  defaultLayout = "unified"
}: DocumentDiffViewerProps) {
  const [layout, setLayout] = useState<DocumentDiffLayout>(defaultLayout)
  const hasDiff = diff !== undefined && diff.hunks.length > 0

  return (
    <div
      className={cn(
        "flex min-h-0 flex-col overflow-hidden rounded-lg border bg-background font-mono text-xs",
        className
      )}
    >
      <div className="flex items-center justify-between gap-3 border-b bg-muted/20 px-3 py-2">
        <span className="text-muted-foreground">
          {diff ? `Version #${diff.baseVersionId} → #${diff.targetVersionId}` : "文档逐行差异"}
        </span>
        <Tabs value={layout} onValueChange={(value) => setLayout(value as DocumentDiffLayout)}>
          <TabsList className="h-7">
            <TabsTrigger value="unified" className="h-6 px-2 text-xs">
              统一
            </TabsTrigger>
            <TabsTrigger value="split" className="h-6 px-2 text-xs">
              并排
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        {loading ? (
          <div className="flex items-center justify-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            正在计算逐行差异
          </div>
        ) : error ? (
          <div className="p-8 text-center text-destructive">版本差异加载失败</div>
        ) : !hasDiff ? (
          <div className="p-8 text-center text-muted-foreground">{emptyMessage}</div>
        ) : (
          <div className="space-y-3">
            {diff.hunks.map((hunk) => (
              <section key={`${hunk.oldStart}-${hunk.newStart}`}>
                <div className="border-y bg-muted px-3 py-1 text-muted-foreground first:border-t-0">
                  @@ -{hunk.oldStart},{hunk.oldCount} +{hunk.newStart},{hunk.newCount} @@
                </div>
                {hunk.lines.map((line, index) =>
                  layout === "unified" ? (
                    <UnifiedLine key={`${line.oldLine}-${line.newLine}-${index}`} line={line} />
                  ) : (
                    <SplitLine key={`${line.oldLine}-${line.newLine}-${index}`} line={line} />
                  )
                )}
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
