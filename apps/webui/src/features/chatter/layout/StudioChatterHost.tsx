/**
 * Studio Chatter 宿主：为项目详情路由注入服务端受控会话上下文。
 * @author AaronZZH & Kiro
 */

"use client"

import { usePathname } from "next/navigation"
import { useAigcProjectConversationContext } from "@/lib/api/rest/ai/aigc"
import type { ChatterProjectContext } from "../types"
import { FloatingChatter } from "./FloatingChatter"

const PROJECT_DETAIL_PATH = /^\/studio\/projects\/([^/]+)\/?$/

function projectIdFromPath(pathname: string): number | null {
  const rawProjectId = PROJECT_DETAIL_PATH.exec(pathname)?.[1]
  if (!rawProjectId) return null
  const projectId = Number(rawProjectId)
  return Number.isFinite(projectId) && projectId > 0 ? projectId : null
}

/** 在 Studio 内挂载唯一 Chatter，并仅向已绑定项目注入固定会话。 */
export function StudioChatterHost() {
  const pathname = usePathname()
  const projectRoute = PROJECT_DETAIL_PATH.test(pathname)
  const projectId = projectIdFromPath(pathname)
  const contextQuery = useAigcProjectConversationContext(projectId)
  const context = contextQuery.data

  if (projectRoute && (projectId === null || context?.state !== "READY")) return null

  const projectContext: ChatterProjectContext | undefined =
    context?.state === "READY"
      ? {
          projectId: context.projectId,
          assistantId: context.assistantId,
          threadId: context.threadId
        }
      : undefined

  return <FloatingChatter availableModes={["panel", "page"]} projectContext={projectContext} />
}
