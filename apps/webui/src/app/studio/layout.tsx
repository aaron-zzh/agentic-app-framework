/**
 * Studio 路由布局。
 *
 * @author AaronZZH & Kiro
 */

import { StudioLayout } from "@/layouts/studio/StudioLayout"

export default function Layout({ children }: { children: React.ReactNode }) {
  return <StudioLayout>{children}</StudioLayout>
}
