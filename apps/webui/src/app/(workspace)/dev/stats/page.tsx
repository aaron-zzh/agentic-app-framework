/**
 * 迭代统计仪表盘（/workspace/dev/stats）
 * 燃尽图 + 任务完成率 + 质量指标 + 提交历史
 * @author AaronZZH & Kiro
 */
"use client"

import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, Ban, CheckCircle2, GitCommit } from "lucide-react"
import { useMemo } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress, ProgressLabel, ProgressValue } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { request } from "@/lib/api/rest/entity/crud"

/** Git 提交记录 */
interface GitLogEntry {
  hash: string
  message: string
  author: string
  date: string
}

/** Mock 燃尽图数据（后续接入真实统计 API） */
const MOCK_BURNDOWN = {
  totalDays: 14,
  totalTasks: 25,
  actual: [25, 24, 22, 20, 19, 18, 16, 14, 13, 11, 9, 7, 5, 3]
}

/** Mock 任务统计 */
const MOCK_STATS = {
  total: 25,
  completed: 22,
  blockers: 0,
  majors: 1
}

/** SVG 燃尽图组件 */
function BurndownChart({
  totalDays,
  totalTasks,
  actual
}: {
  totalDays: number
  totalTasks: number
  actual: number[]
}) {
  const width = 500
  const height = 200
  const padding = { top: 20, right: 20, bottom: 30, left: 40 }
  const chartW = width - padding.left - padding.right
  const chartH = height - padding.top - padding.bottom

  /** 理想线：从 totalTasks 线性降到 0 */
  const idealPoints = Array.from({ length: totalDays }, (_, i) => {
    const x = padding.left + (i / (totalDays - 1)) * chartW
    const y =
      padding.top + (1 - (totalTasks - (totalTasks / (totalDays - 1)) * i) / totalTasks) * chartH
    return `${x},${y}`
  }).join(" ")

  /** 实际线 */
  const actualPoints = actual
    .map((val, i) => {
      const x = padding.left + (i / (totalDays - 1)) * chartW
      const y = padding.top + (1 - val / totalTasks) * chartH
      return `${x},${y}`
    })
    .join(" ")

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" aria-label="统计图表">
      {/* 网格线 */}
      {[0, 0.25, 0.5, 0.75, 1].map((ratio) => (
        <line
          key={ratio}
          x1={padding.left}
          y1={padding.top + ratio * chartH}
          x2={width - padding.right}
          y2={padding.top + ratio * chartH}
          stroke="currentColor"
          strokeOpacity={0.1}
        />
      ))}
      {/* Y 轴标签 */}
      {[0, 0.5, 1].map((ratio) => (
        <text
          key={ratio}
          x={padding.left - 8}
          y={padding.top + ratio * chartH + 4}
          textAnchor="end"
          className="fill-muted-foreground text-[10px]"
        >
          {Math.round(totalTasks * (1 - ratio))}
        </text>
      ))}
      {/* 理想线 */}
      <polyline
        fill="none"
        stroke="currentColor"
        strokeOpacity={0.3}
        strokeDasharray="4 4"
        points={idealPoints}
      />
      {/* 实际线 */}
      <polyline fill="none" stroke="hsl(var(--primary))" strokeWidth={2} points={actualPoints} />
      {/* 图例 */}
      <line
        x1={padding.left}
        y1={height - 8}
        x2={padding.left + 20}
        y2={height - 8}
        stroke="currentColor"
        strokeOpacity={0.3}
        strokeDasharray="4 4"
      />
      <text x={padding.left + 24} y={height - 4} className="fill-muted-foreground text-[10px]">
        理想
      </text>
      <line
        x1={padding.left + 60}
        y1={height - 8}
        x2={padding.left + 80}
        y2={height - 8}
        stroke="hsl(var(--primary))"
        strokeWidth={2}
      />
      <text x={padding.left + 84} y={height - 4} className="fill-muted-foreground text-[10px]">
        实际
      </text>
    </svg>
  )
}

export default function DevStatsPage() {
  const { data: gitLog, isLoading: gitLoading } = useQuery({
    queryKey: ["autodev-git", "log"],
    queryFn: () => request<GitLogEntry[]>("/autodev/git/log?limit=20")
  })

  const completionRate = useMemo(
    () => Math.round((MOCK_STATS.completed / MOCK_STATS.total) * 100),
    []
  )

  return (
    <PageContainer>
      <div className="mb-4">
        <h1 className="font-semibold text-lg">迭代统计</h1>
        <p className="text-muted-foreground text-sm">v0.1.0 迭代概览</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* 燃尽图 */}
        <Card className="lg:col-span-2">
          <CardHeader className="border-b pb-3">
            <CardTitle className="text-sm">燃尽图</CardTitle>
          </CardHeader>
          <CardContent className="pt-4">
            <BurndownChart
              totalDays={MOCK_BURNDOWN.totalDays}
              totalTasks={MOCK_BURNDOWN.totalTasks}
              actual={MOCK_BURNDOWN.actual}
            />
          </CardContent>
        </Card>

        {/* 右侧统计卡片 */}
        <div className="space-y-4">
          {/* 任务完成率 */}
          <Card size="sm">
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm">
                <CheckCircle2 className="size-4 text-green-500" />
                任务完成率
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Progress value={completionRate}>
                <ProgressLabel>
                  {MOCK_STATS.completed}/{MOCK_STATS.total}
                </ProgressLabel>
                <ProgressValue>{(formattedValue) => `${formattedValue}%`}</ProgressValue>
              </Progress>
            </CardContent>
          </Card>

          {/* 质量指标 */}
          <Card size="sm">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">质量指标</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-sm">
                    <Ban className="size-3.5 text-red-500" />
                    Blocker
                  </span>
                  <Badge variant={MOCK_STATS.blockers > 0 ? "destructive" : "secondary"}>
                    {MOCK_STATS.blockers}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-sm">
                    <AlertTriangle className="size-3.5 text-orange-500" />
                    Major
                  </span>
                  <Badge variant={MOCK_STATS.majors > 2 ? "destructive" : "secondary"}>
                    {MOCK_STATS.majors}
                  </Badge>
                </div>
              </div>
              <p className="mt-2 text-muted-foreground text-xs">门控条件：blocker=0 且 major≤2</p>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* 提交历史 */}
      <Card className="mt-4">
        <CardHeader className="border-b pb-3">
          <CardTitle className="text-sm">最近提交</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <ScrollArea className="max-h-80">
            {gitLoading ? (
              <div className="space-y-3 p-4">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : (
              <div className="divide-y">
                {(gitLog ?? []).map((commit) => (
                  <div key={commit.hash} className="flex items-start gap-3 px-4 py-2.5">
                    <GitCommit className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm">{commit.message}</p>
                      <p className="text-muted-foreground text-xs">
                        <span className="font-mono">{commit.hash.slice(0, 7)}</span>
                        {" · "}
                        {commit.author}
                        {" · "}
                        {commit.date}
                      </p>
                    </div>
                  </div>
                ))}
                {(!gitLog || gitLog.length === 0) && (
                  <p className="p-4 text-center text-muted-foreground text-sm">暂无提交记录</p>
                )}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}
