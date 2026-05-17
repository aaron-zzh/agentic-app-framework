/**
 * Dev 布局——开发调试用，含简单顶栏
 */

import { registerDefaultComponents } from "@/features/entity-engine/components/register"
import { DevHeader } from "@/sections/layout/DevHeader"

// 注册默认字段组件
registerDefaultComponents()

export default function DevLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-background">
      <DevHeader />
      <main>{children}</main>
    </div>
  )
}
