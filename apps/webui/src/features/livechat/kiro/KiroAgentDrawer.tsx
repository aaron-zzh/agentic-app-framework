/**
 * KiroAgentDrawer——Kiro Agent 聊天抽屉
 * 从右侧滑入，顶部 agent 角色选择器 + 对话面板
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useQuery } from "@tanstack/react-query"
import { useId, useState } from "react"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { request } from "@/lib/api/client"
import { ChatLayout } from "../ChatLayout"
import { KiroAgentProvider } from "./KiroAgentProvider"

interface KiroAgentDrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** 获取可用 agent 角色列表 */
function useKiroAgents() {
  return useQuery({
    queryKey: ["kiro", "agents"],
    queryFn: () => request<string[]>("/autodev/kiro/agents")
  })
}

export function KiroAgentDrawer({ open, onOpenChange }: KiroAgentDrawerProps) {
  const uid = useId()
  const [agentRole, setAgentRole] = useState("")
  const { data: agents, isLoading } = useKiroAgents()

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[480px] p-0 sm:max-w-[480px]">
        <div className="flex h-full flex-col">
          <SheetHeader className="border-b px-4 py-3">
            <SheetTitle className="text-base">Kiro Agent</SheetTitle>
            <div className="space-y-1.5 pt-2">
              <Label htmlFor={`${uid}-role`} className="text-muted-foreground text-xs">
                Agent 角色
              </Label>
              {isLoading ? (
                <Skeleton className="h-9 w-full" />
              ) : (
                <Select value={agentRole} onValueChange={(v) => setAgentRole(v ?? "")}>
                  <SelectTrigger id={`${uid}-role`}>
                    <SelectValue placeholder="选择角色..." />
                  </SelectTrigger>
                  <SelectContent>
                    {(agents ?? []).map((role) => (
                      <SelectItem key={role} value={role}>
                        {role}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          </SheetHeader>
          <div className="min-h-0 flex-1">
            <KiroAgentProvider agentRole={agentRole || undefined}>
              <ChatLayout drawer />
            </KiroAgentProvider>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
