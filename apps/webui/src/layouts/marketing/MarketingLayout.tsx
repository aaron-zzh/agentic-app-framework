/**
 * 营销页壳层——顶部导航 + 内容 + 页脚。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { FloatingChatter } from "@/features/chatter/layout/FloatingChatter"
import { MarketingFooter } from "./MarketingFooter"
import { MarketingHeader } from "./MarketingHeader"

interface MarketingLayoutProps {
  children: ReactNode
}

export function MarketingLayout({ children }: MarketingLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col">
      <MarketingHeader />
      <main className="flex-1">{children}</main>
      <MarketingFooter />
      <FloatingChatter />
    </div>
  )
}
