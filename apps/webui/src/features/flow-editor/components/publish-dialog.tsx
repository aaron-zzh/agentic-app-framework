/**
 * 工作流发布管理对话框——发布为 Agent + 版本列表 + 激活版本
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useId, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

/** 版本信息 */
interface WorkflowVersion {
  version: number
  processKey: string
  description: string
  active: boolean
  createdAt: string
}

interface PublishDialogProps {
  processKey: string
  flowName: string
}

export function PublishDialog({ processKey, flowName }: PublishDialogProps) {
  const [open, setOpen] = useState(false)

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          发布管理
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>发布管理 - {flowName}</DialogTitle>
        </DialogHeader>
        <Tabs defaultValue="publish">
          <TabsList>
            <TabsTrigger value="publish">发布</TabsTrigger>
            <TabsTrigger value="versions">版本列表</TabsTrigger>
          </TabsList>
          <TabsContent value="publish">
            <PublishForm processKey={processKey} onSuccess={() => setOpen(false)} />
          </TabsContent>
          <TabsContent value="versions">
            <VersionList processKey={processKey} />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}

/** 发布表单 */
function PublishForm({ processKey, onSuccess }: { processKey: string; onSuccess: () => void }) {
  const formId = useId()
  const [description, setDescription] = useState("")
  const [agentName, setAgentName] = useState("")
  const qc = useQueryClient()

  const publish = useMutation({
    mutationFn: async () => {
      const res = await fetch(`${BASE}/api/system/workflow/publish`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ processKey, description, agentName: agentName || undefined })
      })
      if (!res.ok) throw new Error("发布失败")
      return res.json()
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workflow-versions", processKey] })
      onSuccess()
    }
  })

  return (
    <div className="space-y-4 pt-2">
      <div className="space-y-2">
        <Label htmlFor={`${formId}-agent-name`}>Agent 名称（可选）</Label>
        <Input
          id={`${formId}-agent-name`}
          value={agentName}
          onChange={(e) => setAgentName(e.target.value)}
          placeholder="留空则使用流程名称"
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor={`${formId}-publish-desc`}>版本说明</Label>
        <Textarea
          id={`${formId}-publish-desc`}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="描述本次发布的变更..."
          rows={3}
        />
      </div>
      <Button className="w-full" onClick={() => publish.mutate()} disabled={publish.isPending}>
        {publish.isPending ? "发布中..." : "确认发布"}
      </Button>
    </div>
  )
}

/** 版本列表 */
function VersionList({ processKey }: { processKey: string }) {
  const qc = useQueryClient()

  const { data: versions, isLoading } = useQuery({
    queryKey: ["workflow-versions", processKey],
    queryFn: async () => {
      const res = await fetch(`${BASE}/api/system/workflow/versions/${processKey}`)
      if (!res.ok) throw new Error("获取版本列表失败")
      const json = await res.json()
      return json.data as WorkflowVersion[]
    },
    enabled: !!processKey
  })

  const activate = useMutation({
    mutationFn: async (version: number) => {
      const res = await fetch(
        `${BASE}/api/system/workflow/versions/${processKey}/activate/${version}`,
        { method: "POST" }
      )
      if (!res.ok) throw new Error("激活失败")
      return res.json()
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workflow-versions", processKey] })
    }
  })

  if (isLoading) {
    return <p className="py-4 text-center text-muted-foreground text-sm">加载中...</p>
  }

  if (!versions || versions.length === 0) {
    return <p className="py-4 text-center text-muted-foreground text-sm">暂无发布版本</p>
  }

  return (
    <ScrollArea className="max-h-64">
      <div className="space-y-2 pt-2">
        {versions.map((v) => (
          <div key={v.version} className="flex items-center justify-between rounded border p-3">
            <div>
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm">v{v.version}</span>
                {v.active && <Badge variant="default">当前</Badge>}
              </div>
              {v.description && (
                <p className="mt-0.5 text-muted-foreground text-xs">{v.description}</p>
              )}
              <p className="text-muted-foreground text-xs">
                {new Date(v.createdAt).toLocaleString("zh-CN")}
              </p>
            </div>
            {!v.active && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => activate.mutate(v.version)}
                disabled={activate.isPending}
              >
                激活
              </Button>
            )}
          </div>
        ))}
      </div>
    </ScrollArea>
  )
}
