/**
 * WorkspaceSwitcher——工作区切换器
 * @author AaronZZH & Kiro
 *
 * 从 ui-store 读取工作区列表和当前工作区，切换时：
 * 1. 更新 store.currentWorkspace
 * 2. useEntityList 通过 useWorkspaceId() 自动携带新的 workspaceId
 */

"use client"

import { ChevronsUpDown, Plus } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import type { WorkspaceItem } from "@/lib/store/ui-store"
import { useUIStore } from "@/lib/store/ui-store"

export function WorkspaceSwitcher() {
  const current = useUIStore((s) => s.currentWorkspace)
  const workspaces = useUIStore((s) => s.workspaces)
  const setCurrentWorkspace = useUIStore((s) => s.setCurrentWorkspace)

  const list: WorkspaceItem[] = workspaces.length ? workspaces : [{ id: "1", name: "默认工作区" }]
  const active = current ?? list[0]

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-accent"
          />
        }
      >
        <Avatar className="size-6 rounded-md after:hidden">
          <AvatarImage
            src={active.logo ?? "/assets/icons/ChatBc.png"}
            alt={active.name}
            className="object-cover"
          />
          <AvatarFallback className="rounded-md bg-primary/10 font-semibold text-primary text-xs">
            {active.name.slice(0, 1)}
          </AvatarFallback>
        </Avatar>
        <span className="max-w-28 truncate font-medium text-sm">{active.name}</span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
      </DropdownMenuTrigger>

      <DropdownMenuContent align="start" className="w-64 overflow-hidden p-0">
        <div className="p-1.5">
          <DropdownMenuGroup>
            <DropdownMenuLabel>工作区</DropdownMenuLabel>
            {list.map((ws) => (
              <DropdownMenuItem
                key={ws.id}
                onClick={() => setCurrentWorkspace(ws)}
                className="gap-2.5 rounded-md px-2 py-2"
              >
                <Avatar className="size-7 rounded-md after:hidden">
                  <AvatarImage
                    src={ws.logo ?? "/assets/icons/ChatBc.png"}
                    alt={ws.name}
                    className="object-cover"
                  />
                  <AvatarFallback className="rounded-md bg-gradient-to-br from-violet-500 to-indigo-500 font-semibold text-white text-xs">
                    {ws.name.slice(0, 1)}
                  </AvatarFallback>
                </Avatar>
                <span className="flex-1 truncate text-sm">{ws.name}</span>
                {active.id === ws.id && (
                  <Badge variant="outline" className="border-primary/20 bg-primary/10 text-primary">
                    当前
                  </Badge>
                )}
              </DropdownMenuItem>
            ))}
          </DropdownMenuGroup>
        </div>

        <DropdownMenuSeparator className="my-0 h-0 border-t border-dashed bg-transparent" />

        <div className="p-1.5">
          <DropdownMenuItem className="gap-2 rounded-md px-2 py-2">
            <div className="flex size-7 items-center justify-center rounded-md border border-muted-foreground/40 border-dashed bg-background/50">
              <Plus className="size-3.5 text-muted-foreground" />
            </div>
            <span className="text-sm">创建工作区</span>
          </DropdownMenuItem>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
