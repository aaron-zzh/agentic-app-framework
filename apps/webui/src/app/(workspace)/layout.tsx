/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区
 * @author AaronZZH & Kiro
 *
 * 响应式：
 * - ≥768px（md）：固定侧边栏 + 内容区
 * - <768px：侧边栏隐藏，顶栏汉堡菜单点击弹出 Sheet 侧边栏
 */

import { Suspense } from "react"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { registerDefaultComponents } from "@/features/entity-engine/components/register"
import "@/features/entity-engine/entities"
import { AppHeader } from "@/sections/layout/AppHeader"
import { AppSidebar } from "@/sections/layout/AppSidebar"

// 注册默认字段组件（实体注册已在 entities/index.ts side effect 中完成）
registerDefaultComponents()

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Suspense>
        <TopProgressBar />
      </Suspense>
      {/* 桌面端固定侧边栏 */}
      <div className="hidden md:flex">
        <AppSidebar />
      </div>
      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />
        <main className="flex-1 overflow-auto">{children}</main>
      </div>
    </div>
  )
}
