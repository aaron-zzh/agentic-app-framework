/**
 * 审批工作流列表页——待办/已办/我发起 + 统计卡片
 * @author AaronZZH
 */

"use client"

import Link from "next/link"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1 } from "@/components/ui/typography"
import type { ProcessInstanceVO, WorkflowTaskVO } from "@/lib/api/rest/workflow/approval"
import {
  useApprovalHistory,
  useApprovalStats,
  useMyInitiated,
  useMyPendingTasks
} from "@/lib/queries/use-approval"

type Tab = "pending" | "done" | "initiated"

export default function WorkflowPage() {
  const [activeTab, setActiveTab] = useState<Tab>("pending")

  // TODO: 从 auth store 获取当前用户 ID
  const currentUserId = "current-user"

  const { data: pendingTasks, isLoading: pendingLoading } = useMyPendingTasks()
  const { data: historyData, isLoading: historyLoading } = useApprovalHistory()
  const { data: initiatedData, isLoading: initiatedLoading } = useMyInitiated()
  const { data: stats } = useApprovalStats(currentUserId)

  return (
    <PageContainer>
      <TypographyH1 className="mb-6 text-2xl">审批中心</TypographyH1>

      {/* 统计卡片 */}
      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard title="待办数" value={pendingTasks?.length ?? 0} />
        <StatCard title="已通过" value={stats?.approved ?? 0} />
        <StatCard title="已驳回" value={stats?.rejected ?? 0} />
        <StatCard
          title="平均处理时长"
          value={stats?.avgProcessingHours ? `${stats.avgProcessingHours.toFixed(1)}h` : "-"}
        />
      </div>

      {/* Tab 切换 */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as Tab)} className="mb-3">
        <TabsList>
          <TabsTrigger value="pending">
            我的待办
            {pendingTasks && pendingTasks.length > 0 && (
              <Badge variant="secondary" className="ml-1.5 text-[10px]">
                {pendingTasks.length}
              </Badge>
            )}
          </TabsTrigger>
          <TabsTrigger value="done">我的已办</TabsTrigger>
          <TabsTrigger value="initiated">我发起的</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* 列表内容 */}
      <div className="rounded-lg border">
        {activeTab === "pending" && (
          <TaskList tasks={pendingTasks ?? []} loading={pendingLoading} emptyText="暂无待办审批" />
        )}
        {activeTab === "done" && (
          <InstanceList
            instances={historyData?.list ?? []}
            loading={historyLoading}
            emptyText="暂无已办记录"
          />
        )}
        {activeTab === "initiated" && (
          <InstanceList
            instances={initiatedData?.list ?? []}
            loading={initiatedLoading}
            emptyText="暂无发起的流程"
          />
        )}
      </div>
    </PageContainer>
  )
}

/** 统计卡片 */
function StatCard({ title, value }: { title: string; value: string | number }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="font-medium text-muted-foreground text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="font-bold text-2xl">{value}</p>
      </CardContent>
    </Card>
  )
}

/** 待办任务列表 */
function TaskList({
  tasks,
  loading,
  emptyText
}: {
  tasks: WorkflowTaskVO[]
  loading: boolean
  emptyText: string
}) {
  if (loading) {
    return (
      <div className="space-y-3 p-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={`skel-${i}`} className="h-12 w-full" />
        ))}
      </div>
    )
  }

  if (tasks.length === 0) {
    return (
      <Empty className="py-12">
        <EmptyHeader>
          <EmptyTitle>{emptyText}</EmptyTitle>
          <EmptyDescription>所有审批已处理完毕</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <ScrollArea className="max-h-[500px]">
      <ul>
        {tasks.map((task) => (
          <li
            key={task.taskId}
            className="flex items-center justify-between border-b border-dashed px-4 py-3 last:border-0"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-sm">{task.name || "审批任务"}</p>
              <p className="text-muted-foreground text-xs">流程：{task.processInstanceId}</p>
            </div>
            <Button
              size="sm"
              variant="outline"
              nativeButton={false}
              render={
                <Link
                  href={`/module/${task.entityType ?? "workflow"}/${task.entityId ?? task.processInstanceId}`}
                />
              }
            >
              处理
            </Button>
          </li>
        ))}
      </ul>
    </ScrollArea>
  )
}

/** 流程实例列表 */
function InstanceList({
  instances,
  loading,
  emptyText
}: {
  instances: ProcessInstanceVO[]
  loading: boolean
  emptyText: string
}) {
  if (loading) {
    return (
      <div className="space-y-3 p-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={`skel-${i}`} className="h-12 w-full" />
        ))}
      </div>
    )
  }

  if (instances.length === 0) {
    return (
      <Empty className="py-12">
        <EmptyHeader>
          <EmptyTitle>{emptyText}</EmptyTitle>
          <EmptyDescription>没有相关记录</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  /** 状态 Badge 映射 */
  const statusVariant = (status: string) => {
    if (status === "completed") return "secondary" as const
    if (status === "rejected") return "destructive" as const
    return "default" as const
  }

  return (
    <ScrollArea className="max-h-[500px]">
      <ul>
        {instances.map((inst) => (
          <li
            key={inst.processInstanceId}
            className="flex items-center justify-between border-b border-dashed px-4 py-3 last:border-0"
          >
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <p className="truncate font-medium text-sm">
                  {inst.processDefinitionName || inst.processDefinitionKey}
                </p>
                <Badge variant={statusVariant(inst.status)} className="text-[10px]">
                  {inst.status}
                </Badge>
              </div>
              <p className="text-muted-foreground text-xs">发起时间：{inst.startTime}</p>
            </div>
          </li>
        ))}
      </ul>
    </ScrollArea>
  )
}
