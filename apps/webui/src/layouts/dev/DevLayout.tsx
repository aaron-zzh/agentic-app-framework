/**
 * Dev 壳层——开发调试顶栏与主内容区。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { MotionLazy } from "@/components/animate"
import { registerDefaultComponents } from "@/features/entity-engine/components/register"
import { DevHeader } from "./DevHeader"

registerDefaultComponents()

interface DevLayoutProps {
  children: ReactNode
}

export function DevLayout({ children }: DevLayoutProps) {
  return (
    <MotionLazy>
      <div className="min-h-screen bg-background">
        <DevHeader />
        <main>{children}</main>
      </div>
    </MotionLazy>
  )
}
