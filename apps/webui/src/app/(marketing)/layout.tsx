/**
 * 营销页布局——顶部导航 + 内容 + 页脚
 * @author AaronZZH & Kiro
 */

import { FloatingAssistant } from "@/features/floating-assistant/FloatingAssistant"
import { MarketingFooter } from "@/sections/layout/MarketingFooter"
import { MarketingHeader } from "@/sections/layout/MarketingHeader"

export default function MarketingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <MarketingHeader />
      <main className="flex-1">{children}</main>
      <MarketingFooter />
      <FloatingAssistant />
    </div>
  )
}
