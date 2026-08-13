/**
 * 工作区应用壳——组装进度条、客户端主壳体与并行路由弹窗。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { Suspense } from "react"
import { MotionLazy } from "@/components/animate"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { WorkspaceContent } from "./WorkspaceContent"

interface WorkspaceLayoutProps {
  children: ReactNode
  /** parallel route slot：拦截路由弹窗显示在当前页面背景之上。 */
  modal: ReactNode
}

export function WorkspaceLayout({ children, modal }: WorkspaceLayoutProps) {
  return (
    <MotionLazy>
      <Suspense>
        <TopProgressBar />
      </Suspense>
      <WorkspaceContent>{children}</WorkspaceContent>
      {modal}
    </MotionLazy>
  )
}
