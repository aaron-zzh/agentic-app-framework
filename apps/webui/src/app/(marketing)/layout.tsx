/**
 * 营销页路由布局。
 *
 * @author AaronZZH & Kiro
 */

import { MarketingLayout } from "@/layouts/marketing/MarketingLayout"

export default function Layout({ children }: { children: React.ReactNode }) {
  return <MarketingLayout>{children}</MarketingLayout>
}
