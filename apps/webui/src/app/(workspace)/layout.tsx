/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区 + GlobalChatter
 * Server Component：仅注册字段组件。
 * 实体定义由 WorkspaceLayout 从后端元数据加载并注册。
 * 客户端交互（GlobalChatter、主题切换等）由 WorkspaceLayout 处理
 *
 * @author AaronZZH & Kiro
 */

import { Suspense } from "react"
import { MotionLazy } from "@/components/animate"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { registerDefaultComponents } from "@/features/entity-engine/components/register"
import { WorkspaceLayout } from "@/sections/layout/WorkspaceLayout"

// 注册默认字段组件；实体定义由 WorkspaceLayout 从后端元数据加载。
registerDefaultComponents()

export default function Layout({
  children,
  modal
}: {
  children: React.ReactNode
  /** parallel route slot：用于拦截路由弹窗（如 /settings/invite 在原页面背景之上以弹窗形式打开） */
  modal: React.ReactNode
}) {
  return (
    <MotionLazy>
      <Suspense>
        <TopProgressBar />
      </Suspense>
      <WorkspaceLayout>{children}</WorkspaceLayout>
      {modal}
    </MotionLazy>
  )
}
