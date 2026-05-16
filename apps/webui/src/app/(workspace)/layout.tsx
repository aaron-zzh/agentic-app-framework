/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区
 * @author AaronZZH & Kiro
 */

import { Suspense } from "react"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { entityRegistry, sampleEntities } from "@/features/entity-engine"
import { AppHeader } from "@/sections/layout/AppHeader"
import { AppSidebar } from "@/sections/layout/AppSidebar"

// 注册示例实体（后续改为从后端动态加载）
entityRegistry.registerAll(sampleEntities)

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Suspense>
        <TopProgressBar />
      </Suspense>
      <AppSidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />
        <main className="flex-1 overflow-auto">{children}</main>
      </div>
    </div>
  )
}
