/**
 * 画布可视区的"示例数据"标记，提示当前画布展示的是演示数据。
 *
 * 默认绝对定位在可视区左上角；inline 时作为普通行内元素，由调用方排布（如跟在面包屑后面）。
 * @author AaronZZH & Kiro
 */

import { NeonChip } from "@/components/studio"
import { cn } from "@/lib/utils/cn"

interface CanvasMockBadgeProps {
  /** 补充说明，如"绑定角色后显示真实关系" */
  hint?: string
  inline?: boolean
}

export function CanvasMockBadge({ hint, inline = false }: CanvasMockBadgeProps) {
  return (
    <div
      className={cn(
        "pointer-events-auto flex items-center gap-2",
        !inline && "absolute top-0 left-0"
      )}
    >
      <NeonChip tone="amber" size="sm">
        示例数据
      </NeonChip>
      {hint ? <span className="text-[11px] text-muted-foreground">{hint}</span> : null}
    </div>
  )
}
