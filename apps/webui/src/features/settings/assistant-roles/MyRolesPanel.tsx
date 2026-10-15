/**
 * 我的角色面板：管理自己的私有角色（新建、编辑、删除）。
 *
 * 私有角色被管理端下架后仍可在此看到，但不会进入助理的候选路由。
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import type { AiRoleVO } from "@/lib/api/rest/ai/role"
import { useDeleteMyAiRole, useMyAiRoles } from "@/lib/api/rest/ai/role"

import { MyRoleEditorDialog } from "./MyRoleEditorDialog"

export function MyRolesPanel() {
  const { data: roles, isLoading } = useMyAiRoles()
  const deleteRole = useDeleteMyAiRole()
  const [editing, setEditing] = useState<AiRoleVO | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [deleting, setDeleting] = useState<AiRoleVO | null>(null)

  const openEditor = (role: AiRoleVO | null) => {
    setEditing(role)
    setEditorOpen(true)
  }

  const confirmDelete = () => {
    if (!deleting) return
    deleteRole.mutate(deleting.id, {
      // 失败原因（如它仍是默认角色）由请求拦截器统一提示
      onSettled: () => setDeleting(null)
    })
  }

  if (isLoading) {
    return <Skeleton className="h-40 w-full" />
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-3">
          <div>
            <CardTitle>我的私有角色</CardTitle>
            <CardDescription>基于你可见的技能和平台允许的工具，定制只属于你的角色</CardDescription>
          </div>
          <Button onClick={() => openEditor(null)}>新建角色</Button>
        </CardHeader>
        <CardContent>
          {!roles?.length ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>还没有私有角色</EmptyTitle>
                <EmptyDescription>新建后到“角色库”中绑定到助理即可使用。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <ul className="divide-y">
              {roles.map((role) => (
                <li
                  key={role.id}
                  className="flex flex-wrap items-center justify-between gap-3 py-3"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium text-sm">{role.name}</span>
                      {role.status === "disabled" && <Badge variant="destructive">已被下架</Badge>}
                    </div>
                    {role.description && (
                      <p className="line-clamp-2 text-muted-foreground text-xs">
                        {role.description}
                      </p>
                    )}
                    <p className="text-muted-foreground text-xs">
                      {role.skillBindings.length} 个技能
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button size="sm" variant="outline" onClick={() => openEditor(role)}>
                      编辑
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setDeleting(role)}>
                      删除
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <MyRoleEditorDialog open={editorOpen} onOpenChange={setEditorOpen} role={editing} />

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除私有角色</AlertDialogTitle>
            <AlertDialogDescription>
              确认删除“{deleting?.name}
              ”？它在你助理上的绑定会一并解除；如果它是默认角色，需要先切换默认角色。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
