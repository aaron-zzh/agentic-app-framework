/**
 * WorkspaceSwitcher——组织切换器
 * @author AaronZZH & Kiro
 *
 * 切换组织时：
 * 1. 更新 org-store.currentOrgId（持久化到 localStorage）
 * 2. API client 自动从 org-store 读取 X-Org-Id 请求头
 * 3. invalidateQueries 刷新所有数据
 */

"use client"

import { ChevronsUpDown, Plus } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"
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
import { useOrgStore } from "@/lib/store/org-store"
import { useOrganizations } from "@/lib/queries/use-organizations"

export function WorkspaceSwitcher() {
  const queryClient = useQueryClient()
  const currentOrgId = useOrgStore((s) => s.currentOrgId)
  const setCurrentOrgId = useOrgStore((s) => s.setCurrentOrgId)
  const { data: orgs } = useOrganizations()

  const list = orgs ?? []
  const active = list.find((o) => o.id === currentOrgId) ?? list[0]

  /** 切换组织 */
  function handleSwitch(orgId: string) {
    if (orgId === currentOrgId) return
    setCurrentOrgId(orgId)
    queryClient.invalidateQueries()
  }

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
            src={active?.logo ?? "/assets/icons/ChatBc.png"}
            alt={active?.name ?? "组织"}
            className="object-cover"
          />
          <AvatarFallback className="rounded-md bg-primary/10 font-semibold text-primary text-xs">
            {active?.name?.slice(0, 1) ?? "O"}
          </AvatarFallback>
        </Avatar>
        <span className="max-w-28 truncate font-medium text-sm">
          {active?.name ?? "选择组织"}
        </span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
      </DropdownMenuTrigger>

      <DropdownMenuContent align="start" className="w-64 overflow-hidden p-0">
        <div className="p-1.5">
          <DropdownMenuGroup>
            <DropdownMenuLabel>组织</DropdownMenuLabel>
            {list.map((org) => (
              <DropdownMenuItem
                key={org.id}
                onClick={() => handleSwitch(org.id)}
                className="gap-2.5 rounded-md px-2 py-2"
              >
                <Avatar className="size-7 rounded-md after:hidden">
                  <AvatarImage
                    src={org.logo ?? "/assets/icons/ChatBc.png"}
                    alt={org.name}
                    className="object-cover"
                  />
                  <AvatarFallback className="rounded-md bg-gradient-to-br from-violet-500 to-indigo-500 font-semibold text-white text-xs">
                    {org.name.slice(0, 1)}
                  </AvatarFallback>
                </Avatar>
                <span className="flex-1 truncate text-sm">{org.name}</span>
                {active?.id === org.id && (
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
            <span className="text-sm">创建组织</span>
          </DropdownMenuItem>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
