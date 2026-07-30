"use client"

import { GitBranch, Loader2, Play, Plus, Trash2 } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type FlowDefinition,
  useFlowDelete,
  useFlowDeploy,
  useFlowList,
  useFlowSave
} from "@/features/flow-editor"

const NEW_FLOW: FlowDefinition = {
  nodes: [
    { id: "start", type: "start", position: { x: 120, y: 180 }, data: { label: "开始" } },
    { id: "end", type: "end", position: { x: 520, y: 180 }, data: { label: "结束" } }
  ],
  edges: [{ id: "start_to_end", source: "start", target: "end" }],
  viewport: { x: 0, y: 0, zoom: 1 }
}

export function FlowListView() {
  const router = useRouter()
  const { data: flows, isLoading } = useFlowList()
  const save = useFlowSave()
  const deploy = useFlowDeploy()
  const remove = useFlowDelete()

  const createFlow = async () => {
    try {
      const flow = await save.mutateAsync({
        name: "未命名工作流",
        mode: "CHAT",
        definition: NEW_FLOW
      })
      router.push(`/workflow/design/${flow.id}`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "创建工作流失败")
    }
  }

  const deployFlow = async (id: string) => {
    try {
      await deploy.mutateAsync(id)
      toast.success("工作流已部署")
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "部署失败")
    }
  }

  const deleteFlow = async (id: string) => {
    if (!window.confirm("确定删除此工作流吗？")) return
    try {
      await remove.mutateAsync(id)
      toast.success("工作流已删除")
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除失败")
    }
  }

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between gap-3">
        <div>
          <h1 className="font-semibold text-2xl tracking-tight">工作流设计</h1>
          <p className="mt-1 text-muted-foreground text-sm">编排、调试并部署 AI 工作流</p>
        </div>
        <Button onClick={() => void createFlow()} disabled={save.isPending}>
          {save.isPending ? <Loader2 className="animate-spin" /> : <Plus />}
          新建工作流
        </Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-44" />
          ))}
        </div>
      ) : !flows?.length ? (
        <Empty className="rounded-lg border py-16">
          <EmptyHeader>
            <EmptyTitle>暂无工作流</EmptyTitle>
            <EmptyDescription>创建第一个工作流并在可视化画布中编排节点。</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {flows.map((flow) => (
            <Card key={flow.id} className="transition-colors hover:border-primary/40">
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <GitBranch className="size-5 shrink-0 text-primary" />
                    <CardTitle className="truncate text-base">{flow.name}</CardTitle>
                  </div>
                  <Badge variant={flow.status === "PUBLISHED" ? "default" : "secondary"}>
                    {flow.status === "PUBLISHED" ? "已部署" : "草稿"}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <p className="line-clamp-2 min-h-10 text-muted-foreground text-sm">
                  {flow.description || "暂无描述"}
                </p>
                <p className="mt-3 text-muted-foreground text-xs">
                  更新于 {new Date(flow.updateTime).toLocaleString("zh-CN")}
                </p>
                <div className="mt-4 flex items-center gap-2">
                  <Button
                    size="sm"
                    nativeButton={false}
                    render={<Link href={`/workflow/design/${flow.id}`} />}
                  >
                    编辑
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => void deployFlow(flow.id)}
                    disabled={deploy.isPending}
                  >
                    <Play />
                    部署
                  </Button>
                  <Button
                    size="icon-sm"
                    variant="ghost"
                    className="ml-auto text-destructive"
                    aria-label={`删除 ${flow.name}`}
                    onClick={() => void deleteFlow(flow.id)}
                    disabled={remove.isPending}
                  >
                    <Trash2 />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </PageContainer>
  )
}
