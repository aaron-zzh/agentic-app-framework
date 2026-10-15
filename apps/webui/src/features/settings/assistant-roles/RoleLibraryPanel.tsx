/**
 * 角色库面板：按"内置 / 公开 / 我的"分组展示可绑定角色，支持绑定、解绑、启停与设为默认。
 *
 * 数据全部来自服务端（TanStack Query），不复制进客户端 store；默认角色不可解绑也不可停用。
 * @author AaronZZH & Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import type { AiRoleLibraryItem, AiRoleVisibility } from "@/lib/api/rest/ai/role"
import {
  useAiRoleLibrary,
  useBindAssistantRole,
  useUnbindAssistantRole,
  useUpdateAssistantRoleBinding
} from "@/lib/api/rest/ai/role"

const GROUPS: { visibility: AiRoleVisibility; title: string; description: string }[] = [
  { visibility: "SYSTEM", title: "内置角色", description: "平台维护，所有用户可绑定" },
  { visibility: "PUBLIC", title: "公开角色", description: "管理员发布，所有用户可绑定" },
  { visibility: "PRIVATE", title: "我的私有角色", description: "仅你自己可见和绑定" }
]

export function RoleLibraryPanel() {
  const { data: library, isLoading } = useAiRoleLibrary()
  const bindRole = useBindAssistantRole()
  const unbindRole = useUnbindAssistantRole()
  const updateBinding = useUpdateAssistantRoleBinding()

  if (isLoading || !library) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  const assistantId = library.assistantId
  if (assistantId === null) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyTitle>暂无可维护的助理</EmptyTitle>
          <EmptyDescription>创建默认助理后即可在这里绑定角色。</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  const atLimit = library.boundCount >= library.maxRoles
  const busy = bindRole.isPending || unbindRole.isPending || updateBinding.isPending

  const renderActions = (item: AiRoleLibraryItem) => {
    if (!item.bound) {
      return (
        <Button
          size="sm"
          disabled={busy || atLimit}
          onClick={() => bindRole.mutate({ assistantId, roleId: item.role.id })}
        >
          绑定
        </Button>
      )
    }
    return (
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2 text-xs">
          <span>启用</span>
          <Switch
            checked={item.enabled}
            disabled={busy || item.defaultRole}
            aria-label={`启用角色 ${item.role.name}`}
            onCheckedChange={(checked) =>
              updateBinding.mutate({ assistantId, roleId: item.role.id, enabled: checked })
            }
          />
        </div>
        {!item.defaultRole && (
          <Button
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={() =>
              updateBinding.mutate({ assistantId, roleId: item.role.id, defaultRole: true })
            }
          >
            设为默认
          </Button>
        )}
        <Button
          size="sm"
          variant="ghost"
          disabled={busy || item.defaultRole}
          onClick={() => unbindRole.mutate({ assistantId, roleId: item.role.id })}
        >
          解绑
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Badge variant={atLimit ? "destructive" : "secondary"}>
          已绑定 {library.boundCount} / {library.maxRoles}
        </Badge>
        {atLimit && (
          <p className="text-muted-foreground text-sm">已达绑定上限，请先解绑不需要的角色。</p>
        )}
      </div>

      {GROUPS.map((group) => {
        const items = library.items.filter((item) => item.role.visibility === group.visibility)
        return (
          <Card key={group.visibility}>
            <CardHeader>
              <CardTitle>{group.title}</CardTitle>
              <CardDescription>{group.description}</CardDescription>
            </CardHeader>
            <CardContent>
              {items.length === 0 ? (
                <p className="py-4 text-center text-muted-foreground text-sm">暂无角色</p>
              ) : (
                <ul className="divide-y">
                  {items.map((item) => (
                    <li
                      key={item.role.id}
                      className="flex flex-wrap items-center justify-between gap-3 py-3"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium text-sm">{item.role.name}</span>
                          {item.defaultRole && <Badge>默认</Badge>}
                          {item.bound && !item.enabled && <Badge variant="outline">已停用</Badge>}
                        </div>
                        {item.role.description && (
                          <p className="line-clamp-2 text-muted-foreground text-xs">
                            {item.role.description}
                          </p>
                        )}
                        <p className="text-muted-foreground text-xs">
                          {item.role.skillBindings.length} 个技能
                        </p>
                      </div>
                      {renderActions(item)}
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
