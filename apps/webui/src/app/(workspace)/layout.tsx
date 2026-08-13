/**
 * 工作区路由布局。
 *
 * @author AaronZZH & Kiro
 */

import { WorkspaceLayout } from "@/layouts/workspace/WorkspaceLayout"

export default function Layout({
  children,
  modal
}: {
  children: React.ReactNode
  modal: React.ReactNode
}) {
  return <WorkspaceLayout modal={modal}>{children}</WorkspaceLayout>
}
