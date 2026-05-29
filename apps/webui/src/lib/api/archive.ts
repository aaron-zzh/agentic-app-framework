/**
 * 数据归档 API 客户端
 * @author AaronZZH & Kiro
 */

import { useUIStore } from "@/lib/store/ui-store"
import { ApiError } from "./client"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const workspaceId = useUIStore.getState().currentWorkspace?.id
  const res = await fetch(`${BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(workspaceId && { "X-Workspace-Id": workspaceId }),
      ...init?.headers
    },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const archiveApi = {
  /** 归档记录 */
  archive: (entity: string, id: string) =>
    req<void>(`/${entity}/${id}/archive`, { method: "POST" }),

  /** 恢复到活跃 */
  unarchive: (entity: string, id: string) =>
    req<void>(`/${entity}/${id}/unarchive`, { method: "POST" })
}
