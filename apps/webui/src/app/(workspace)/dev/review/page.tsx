"use client"

/**
 * AI 产出总览——查看所有助理工作成果，支持筛选、调整、回退
 * 路由：/workspace/dev/review
 */

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { request } from "@/lib/api/client"

interface AiOutput {
  id: number
  sourceType: string
  category: string
  riskLevel: string
  title: string
  description: string | null
  status: string
  createTime: string
  contentSnapshot: string | null
}

const RISK_ICON: Record<string, string> = { high: "🔴", medium: "🟡", low: "🟢" }
const CATEGORY_LABEL: Record<string, string> = {
  code: "代码",
  document: "文档",
  entity_change: "实体变更",
  config: "配置",
  file: "文件"
}
const SOURCE_LABEL: Record<string, string> = {
  autodev: "Auto-Dev",
  task: "任务",
  chat: "对话",
  tool: "工具"
}

export default function AiOutputsPage() {
  const [category, setCategory] = useState<string>("all")
  const [riskLevel, setRiskLevel] = useState<string>("all")
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ["ai-outputs", category, riskLevel],
    queryFn: () => {
      const params = new URLSearchParams()
      if (category !== "all") params.set("category", category)
      if (riskLevel !== "all") params.set("riskLevel", riskLevel)
      params.set("size", "50")
      return request<{ content: AiOutput[] }>(`/ai-outputs?${params.toString()}`)
    }
  })

  const revertMutation = useMutation({
    mutationFn: (id: number) => request(`/ai-outputs/${id}/revert`, { method: "POST", body: JSON.stringify({ reason: "用户回退" }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["ai-outputs"] })
  })

  const outputs = data?.content ?? []

  const { data: stats } = useQuery({
    queryKey: ["ai-outputs-stats"],
    queryFn: () => request<{ high: number; medium: number; low: number }>("/ai-outputs/stats")
  })

  const statData = stats

  return (
    <PageContainer maxWidth="lg">
      <div className="mb-6 space-y-2">
        <TypographyH1>AI 工作产出</TypographyH1>
        <TypographyMuted>
          查看所有助理的工作成果，高风险项标红提醒，支持调整和回退
          {statData && (
            <span className="ml-4">
              🔴 {statData.high} 🟡 {statData.medium} 🟢 {statData.low}
            </span>
          )}
        </TypographyMuted>
      </div>

      {/* 筛选 */}
      <div className="mb-4 flex gap-3">
        <Select value={category} onValueChange={(v) => setCategory(v ?? "all")}>
          <SelectTrigger className="w-32">
            <SelectValue placeholder="类别" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部类别</SelectItem>
            <SelectItem value="code">代码</SelectItem>
            <SelectItem value="document">文档</SelectItem>
            <SelectItem value="entity_change">实体变更</SelectItem>
            <SelectItem value="config">配置</SelectItem>
          </SelectContent>
        </Select>

        <Select value={riskLevel} onValueChange={(v) => setRiskLevel(v ?? "all")}>
          <SelectTrigger className="w-32">
            <SelectValue placeholder="风险" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部风险</SelectItem>
            <SelectItem value="high">🔴 高风险</SelectItem>
            <SelectItem value="medium">🟡 中风险</SelectItem>
            <SelectItem value="low">🟢 低风险</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* 产出列表 */}
      <Card>
        <CardHeader>
          <CardTitle>产出时间线</CardTitle>
        </CardHeader>
        <CardContent>
          <ScrollArea className="h-[600px]">
            {isLoading ? (
              <p className="text-muted-foreground py-8 text-center text-sm">加载中...</p>
            ) : outputs.length === 0 ? (
              <p className="text-muted-foreground py-8 text-center text-sm">暂无产出记录</p>
            ) : (
              <div className="space-y-3">
                {outputs.map((output: AiOutput) => (
                  <div
                    key={output.id}
                    className="flex items-start gap-3 rounded-lg border p-3 hover:bg-muted/50"
                  >
                    <span className="mt-0.5 text-lg">{RISK_ICON[output.riskLevel] ?? "🟢"}</span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">{output.title}</span>
                        <Badge variant="outline" className="text-xs">
                          {CATEGORY_LABEL[output.category] ?? output.category}
                        </Badge>
                        <Badge variant="secondary" className="text-xs">
                          {SOURCE_LABEL[output.sourceType] ?? output.sourceType}
                        </Badge>
                        {output.status !== "effective" && (
                          <Badge variant="destructive" className="text-xs">
                            {output.status === "reverted" ? "已回退" : "已调整"}
                          </Badge>
                        )}
                        <span className="text-muted-foreground ml-auto text-xs">
                          {new Date(output.createTime).toLocaleString()}
                        </span>
                      </div>
                      {output.description && (
                        <p className="text-muted-foreground mt-1 text-xs">{output.description}</p>
                      )}
                    </div>
                    {output.status === "effective" && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="shrink-0 text-xs"
                        onClick={() => revertMutation.mutate(output.id)}
                      >
                        回退
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}
