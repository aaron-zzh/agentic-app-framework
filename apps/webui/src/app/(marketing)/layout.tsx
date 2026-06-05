/**
 * 营销页布局——顶部导航 + 内容 + 页脚 + 客服浮窗
 * @author AaronZZH & Kiro
 */

import { LivechatWidget } from "@/features/livechat/LivechatWidget"
import { MarketingFooter } from "@/sections/layout/MarketingFooter"
import { MarketingHeader } from "@/sections/layout/MarketingHeader"

export default function MarketingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <MarketingHeader />
      <main className="flex-1">{children}</main>
      <MarketingFooter />
      <LivechatWidget />
    </div>
  )
}
