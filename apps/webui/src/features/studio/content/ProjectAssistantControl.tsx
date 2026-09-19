/**
 * 项目工作台的受控 Assistant 入口：未绑定时显式选择，绑定后只打开服务端固定会话。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Loader2, MessageSquare, Sparkles } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { useAssistants } from "@/lib/api/rest/ai"
import {
  useAigcProjectConversationContext,
  useBindAigcProjectConversation
} from "@/lib/api/rest/ai/aigc"
import { useChatterStore } from "@/lib/store/chatter-store"

export interface ProjectAssistantControlProps {
  projectId: number
  canBind: boolean
}

export function ProjectAssistantControl({
  projectId,
  canBind
}: ProjectAssistantControlProps) {
  const bindingDialog = useBoolean(false)
  const [selectedAssistantId, setSelectedAssistantId] = useState("")
  const open = useChatterStore((state) => state.open)
  const setOpen = useChatterStore((state) => state.setOpen)
  const contextQuery = useAigcProjectConversationContext(projectId)
  const assistantsQuery = useAssistants()
  const bindConversation = useBindAigcProjectConversation()
  const context = contextQuery.data

  async function bindAssistant() {
    if (!context || context.state !== "NEEDS_ASSISTANT_BINDING" || !selectedAssistantId) return
    try {
      await bindConversation.mutateAsync({
        projectId,
        expectedProjectVersion: context.projectVersion,
        assistantId: selectedAssistantId
      })
      bindingDialog.onFalse()
      setSelectedAssistantId("")
      toast.success("Assistant 已绑定，可打开项目对话")
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "绑定 Assistant 失败")
    }
  }

  if (contextQuery.isLoading) return <Skeleton className="h-7 w-32" />

  if (contextQuery.isError || !context) {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => void contextQuery.refetch()}
      >
        <MessageSquare />
        重试 Assistant
      </Button>
    )
  }

  if (context.state === "READY") {
    return (
      <div className="flex items-center gap-2">
        <span className="flex items-center gap-1.5 text-muted-foreground text-xs" role="status">
          <MessageSquare />
          项目 Assistant 已绑定
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          aria-pressed={open}
          onClick={() => setOpen(!open)}
        >
          {open ? "收起对话" : "展开对话"}
        </Button>
      </div>
    )
  }

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={!canBind}
        aria-describedby={!canBind ? "project-assistant-bind-reason" : undefined}
        onClick={bindingDialog.onTrue}
      >
        <Sparkles />
        选择 Assistant
      </Button>
      {!canBind ? (
        <span id="project-assistant-bind-reason" className="text-muted-foreground text-xs">
          无项目更新权限
        </span>
      ) : null}
      <Dialog open={bindingDialog.value} onOpenChange={bindingDialog.setValue}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>为项目选择 Assistant</DialogTitle>
            <DialogDescription>
              绑定后，本项目始终使用服务端创建的固定对话；不能回退到默认 Assistant。
            </DialogDescription>
          </DialogHeader>

          {assistantsQuery.isError ? (
            <Alert variant="destructive">
              <AlertTitle>Assistant 列表加载失败</AlertTitle>
              <AlertDescription>请重试后再选择，页面不会自动绑定默认 Assistant。</AlertDescription>
            </Alert>
          ) : assistantsQuery.isSuccess && assistantsQuery.data.length === 0 ? (
            <Alert>
              <AlertTitle>暂无可用 Assistant</AlertTitle>
              <AlertDescription>项目保持未绑定，请先配置可执行的 Assistant。</AlertDescription>
            </Alert>
          ) : (
            <Select
              value={selectedAssistantId || null}
              disabled={assistantsQuery.isLoading || bindConversation.isPending}
              onValueChange={(value) => setSelectedAssistantId(value ?? "")}
            >
              <SelectTrigger aria-label="选择项目 Assistant" className="w-full">
                <SelectValue>
                  {assistantsQuery.data?.find((assistant) => assistant.assistantId === selectedAssistantId)
                    ?.name ?? "请选择 Assistant"}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {(assistantsQuery.data ?? []).map((assistant) => (
                    <SelectItem key={assistant.assistantId} value={assistant.assistantId}>
                      <span className="flex min-w-0 flex-col">
                        <span className="truncate">{assistant.name}</span>
                        <span className="truncate text-muted-foreground text-xs">
                          {assistant.defaultRoleKey}
                        </span>
                      </span>
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={bindingDialog.onFalse}>
              取消
            </Button>
            <Button
              type="button"
              disabled={
                !selectedAssistantId ||
                assistantsQuery.isLoading ||
                assistantsQuery.isError ||
                bindConversation.isPending
              }
              onClick={() => void bindAssistant()}
            >
              {bindConversation.isPending ? <Loader2 className="animate-spin" /> : <Sparkles />}
              {bindConversation.isPending ? "绑定中…" : "确认绑定"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
