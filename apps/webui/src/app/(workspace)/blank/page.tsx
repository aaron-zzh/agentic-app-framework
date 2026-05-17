/**
 * Blank——空白演示页面，验证布局容器效果
 */

import { PageContainer } from "@/components/common/PageContainer"

export default function BlankPage() {
  return (
    <PageContainer maxWidth="xl">
      <h1 className="font-bold text-2xl">Blank</h1>
      <p className="mt-1 text-muted-foreground text-sm">
        空白页面，用于验证工作区布局和 PageContainer 容器效果。
      </p>
      <div className="mt-6 flex h-80 items-center justify-center rounded-lg border border-dashed bg-muted/5">
        <span className="text-muted-foreground text-sm">内容区域</span>
      </div>
    </PageContainer>
  )
}
