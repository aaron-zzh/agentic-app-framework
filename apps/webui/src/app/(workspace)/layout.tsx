/**
 * 工作区布局——侧边栏 + 顶栏 + 主内容区
 * @author AaronZZH & Kiro
 */

import { entityRegistry, sampleEntities } from "@/features/entity-engine"

// 注册示例实体（后续改为从后端动态加载）
entityRegistry.registerAll(sampleEntities)

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen">
      <aside className="w-60 border-r bg-sidebar p-4">
        <div className="text-sm font-medium text-sidebar-foreground">AAF</div>
      </aside>
      <main className="flex-1 overflow-auto">{children}</main>
    </div>
  )
}
