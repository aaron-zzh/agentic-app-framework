/**
 * Studio 首页水合前骨架：页头、指标条与面板区占位。
 * @author AaronZZH & Kiro
 */

import { Skeleton } from "@/components/ui/skeleton"

export function HomeSkeleton() {
  return (
    <div className="flex flex-col gap-6" aria-hidden="true">
      <Skeleton className="h-8 w-64 max-w-full" />
      <Skeleton className="h-[104px] rounded-xl" />
      <Skeleton className="h-80 rounded-xl" />
    </div>
  )
}
