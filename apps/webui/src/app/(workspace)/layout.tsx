/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区 + GlobalChatter
 * Server Component：只做注册和静态初始化
 * 客户端交互（GlobalChatter、主题切换等）由 WorkspaceLayoutClient 处理
 *
 * @author AaronZZH & Kiro
 */

import { Suspense } from "react"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { registerDefaultComponents } from "@/features/entity-engine/components/register"
import "@/features/entity-engine/entities"
import { MotionLazy } from "@/components/animate"
import { WorkspaceLayoutClient } from "@/sections/layout/WorkspaceLayoutClient"

// 注册默认字段组件（实体注册已在 entities/index.ts side effect 中完成）
registerDefaultComponents()

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <MotionLazy>
      <Suspense>
        <TopProgressBar />
      </Suspense>
      <WorkspaceLayoutClient>{children}</WorkspaceLayoutClient>
    </MotionLazy>
  )
}
