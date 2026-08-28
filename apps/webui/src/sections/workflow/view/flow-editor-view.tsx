"use client"

import { ArrowLeft, Bug, Loader2, Rocket, Save } from "lucide-react"
import Link from "next/link"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  type FlowDefinition,
  FlowEditor,
  useFlowDeploy,
  useFlowDetail,
  useFlowSave,
  useWorkflowRuntime,
  WorkflowChat,
  workflowNodeRegistry
} from "@/features/flow-editor"

interface FlowEditorViewProps {
  flowId: string
}

export function FlowEditorView({ flowId }: FlowEditorViewProps) {
  const { data: flow, isLoading, error } = useFlowDetail(flowId)
  const save = useFlowSave()
  const deploy = useFlowDeploy()
  const runtime = useWorkflowRuntime()
  const [name, setName] = useState("")
  const [definition, setDefinition] = useState<FlowDefinition | null>(null)

  useEffect(() => {
    if (!flow) return
    setName(flow.name)
    setDefinition(flow.definition)
  }, [flow])

  const saveDraft = async () => {
    if (!flow || !definition || !name.trim()) return null
    const saved = await save.mutateAsync({
      id: flow.id,
      name: name.trim(),
      description: flow.description,
      mode: flow.mode,
      definition,
      agentCallable: flow.agentCallable,
      requireConfirm: flow.requireConfirm
    })
    toast.success("草稿已保存")
    return saved
  }

  const handleSave = async () => {
    try {
      await saveDraft()
    } catch (saveError) {
      toast.error(saveError instanceof Error ? saveError.message : "保存失败")
    }
  }

  const handleDeploy = async () => {
    try {
      const saved = await saveDraft()
      if (!saved) return
      await deploy.mutateAsync(saved.id)
      toast.success("工作流已编译并部署")
    } catch (deployError) {
      toast.error(deployError instanceof Error ? deployError.message : "部署失败")
    }
  }

  const handleDebug = async () => {
    try {
      const saved = await saveDraft()
      if (!saved) return
      runtime.startWorkflow(saved.id, {})
    } catch (debugError) {
      toast.error(debugError instanceof Error ? debugError.message : "调试启动失败")
    }
  }

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-4rem)] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (error || !flow || !definition) {
    return (
      <div className="flex h-[calc(100vh-4rem)] flex-col items-center justify-center gap-3">
        <p className="text-destructive">
          {error instanceof Error ? error.message : "工作流不存在"}
        </p>
        <Button nativeButton={false} variant="outline" render={<Link href="/workflow/design" />}>
          返回列表
        </Button>
      </div>
    )
  }

  return (
    <div className="flex h-[calc(100vh-4rem)] min-h-0 flex-col p-3">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Button
          size="icon-sm"
          variant="ghost"
          nativeButton={false}
          render={<Link href="/workflow/design" />}
          aria-label="返回工作流列表"
        >
          <ArrowLeft />
        </Button>
        <Input
          value={name}
          onChange={(event) => setName(event.target.value)}
          className="h-8 w-64 font-medium"
          aria-label="工作流名称"
        />
        <span className="text-muted-foreground text-xs">
          {flow.status === "PUBLISHED" ? "已部署" : "草稿"}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => void handleDebug()}
            disabled={save.isPending || runtime.status === "running"}
          >
            <Bug />
            调试
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => void handleSave()}
            disabled={save.isPending}
          >
            {save.isPending ? <Loader2 className="animate-spin" /> : <Save />}
            保存
          </Button>
          <Button
            size="sm"
            onClick={() => void handleDeploy()}
            disabled={save.isPending || deploy.isPending}
          >
            {deploy.isPending ? <Loader2 className="animate-spin" /> : <Rocket />}
            部署
          </Button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 gap-3">
        <div className="min-w-0 flex-1">
          <FlowEditor
            key={flow.id}
            mode="workflow"
            nodeRegistry={workflowNodeRegistry}
            initialData={flow.definition}
            onChange={setDefinition}
            executionState={runtime.executionState}
          />
        </div>
        {(runtime.status !== "idle" || runtime.messages.length > 0) && (
          <div className="w-80 shrink-0 overflow-hidden rounded-lg border">
            <WorkflowChat
              messages={runtime.messages}
              status={runtime.status}
              onSubmitInput={runtime.submitInput}
            />
          </div>
        )}
      </div>
    </div>
  )
}
