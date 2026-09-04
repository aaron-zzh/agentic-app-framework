/**
 * Studio 路由布局。
 *
 * @author AaronZZH & Kiro
 */

import { StudioLayout } from "@/layouts/studio/StudioLayout"

export default function Layout({
  children,
  modal
}: {
  children: React.ReactNode
  modal: React.ReactNode
}) {
  return (
    <StudioLayout>
      {children}
      {modal}
    </StudioLayout>
  )
}
