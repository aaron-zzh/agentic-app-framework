/**
 * AIGC 图像生成页面
 * @author AaronZZH & Kiro
 */

import { AigcLayout } from "@/features/aigc"

export default function AigcPage() {
  return (
    <div className="h-[calc(100vh-var(--layout-header-height))]">
      <AigcLayout />
    </div>
  )
}
