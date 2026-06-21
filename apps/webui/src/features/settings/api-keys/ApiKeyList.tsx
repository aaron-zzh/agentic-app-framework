/**
 * API Key 管理组件——列表 + 生成按钮。
 * 可嵌入用户设置页或用户详情页。
 */

"use client"

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { toast } from "sonner"
import { ActionButton } from "@/components/common/ActionButton"
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
import { request } from "@/lib/api/rest/entity/crud"
import type { ActionConfig } from "@/lib/hooks/use-action"

interface ApiKeyVO {
  id: number
  prefix: string
  name: string
  permissions: string
  createdAt: string
  expiresAt: string | null
  enabled: boolean
  lastUsedAt: string | null
}

interface ApiKeyListProps {
  /** 当前用户 ID（作为上下文传递给生成 API） */
  userId?: number
}

export function ApiKeyList({ userId: _userId }: ApiKeyListProps) {
  const queryClient = useQueryClient()
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  const { data: keys = [], isLoading } = useQuery({
    queryKey: ["api-keys"],
    queryFn: () => request<ApiKeyVO[]>("/v1/api-keys")
  })

  async function handleDelete(id: number) {
    try {
      await request(`/v1/api-keys/${id}`, { method: "DELETE" })
      toast.success("API Key 已删除")
      setDeleteTarget(null)
      queryClient.invalidateQueries({ queryKey: ["api-keys"] })
    } catch {
      // 错误由 backend-client 拦截器统一处理
    }
  }

  // 生成 Key 的 Action 配置
  const generateKeyAction: ActionConfig = {
    id: "generate_api_key",
    label: "生成 API Key",
    type: "dialog",
    api: "/v1/api-keys",
    method: "POST",
    onSuccess: "showOnce",
    form: {
      name: { type: "string", label: "Key 名称", required: true, placeholder: "如：本地采集服务" },
      permissions: {
        type: "select",
        label: "权限范围",
        options: ["ingest,read", "ingest,read,write", "read"],
        defaultValue: "ingest,read"
      },
      expiresInDays: { type: "number", label: "有效期(天)", placeholder: "留空永不过期" }
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-lg">API Key</h3>
        <ActionButton
          config={generateKeyAction}
          size="sm"
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ["api-keys"] })}
        />
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">加载中...</p>
      ) : keys.length === 0 ? (
        <p className="text-muted-foreground text-sm">暂无 API Key，点击上方按钮生成。</p>
      ) : (
        <div className="space-y-2">
          {keys.map((key) => (
            <div key={key.id} className="flex items-center justify-between rounded border p-3">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-sm">{key.name}</span>
                  <Badge variant={key.enabled ? "default" : "secondary"}>
                    {key.enabled ? "启用" : "已禁用"}
                  </Badge>
                </div>
                <div className="flex items-center gap-3 text-muted-foreground text-xs">
                  <code>{key.prefix}</code>
                  <span>权限: {key.permissions}</span>
                  {key.expiresAt && (
                    <span>过期: {new Date(key.expiresAt).toLocaleDateString()}</span>
                  )}
                  {key.lastUsedAt && (
                    <span>最后使用: {new Date(key.lastUsedAt).toLocaleDateString()}</span>
                  )}
                </div>
              </div>
              <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(key.id)}>
                删除
              </Button>
            </div>
          ))}
        </div>
      )}

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除此 Key？删除后使用该 Key 的服务将无法访问。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (deleteTarget) handleDelete(deleteTarget)
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
