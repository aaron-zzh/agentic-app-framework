/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区 + GlobalChatter
 * Server Component：只做注册和静态初始化
 * 客户端交互（GlobalChatter、主题切换等）由 WorkspaceLayout 处理
 *
 * @author AaronZZH & Kiro
 */

import { Suspense } from "react"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { registerDefaultComponents } from "@/features/entity-engine/components/register"
// side-effect import：导入即触发 entityRegistry.registerAll()，确保视图引擎渲染前所有实体已注册
import "@/features/entity-engine/entities"
import { MotionLazy } from "@/components/animate"
import { AuthProvider } from "@/lib/auth/AuthProvider"
import { WorkspaceLayout } from "@/sections/layout/WorkspaceLayout"

// 注册默认字段组件（实体注册已在 entities/index.ts side effect 中完成）
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
      <AuthProvider>
        <WorkspaceLayout>{children}</WorkspaceLayout>
        {modal}
      </AuthProvider>
    </MotionLazy>
  )
}
