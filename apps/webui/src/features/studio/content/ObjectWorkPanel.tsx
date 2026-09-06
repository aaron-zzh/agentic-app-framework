/**
 * DeliverableSet 的精确 Manifest Work 状态。
 * 收录动作统一由项目交付面板在 approved、non-stale Review 后执行。
 * @author AaronZZH & Kiro
 */

"use client"

import { Library } from "lucide-react"
import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcWorks } from "@/lib/api/rest/ai/aigc"

export function ObjectWorkPanel({
  project,
  object
}: {
  project: AigcProject
  object: AigcProjectObject
}) {
  const { data, isLoading } = useAigcWorks({ projectId: project.id })
  const work = data?.list.find(
    (item) =>
      item.deliverableSetObjectId === object.id &&
      item.manifestObjectVersionId === object.adoptedVersionId
  )

  if (object.objectType !== "deliverable_set")
    return (
      <p className="text-muted-foreground text-sm">
        Work 只固定 DeliverableSet 的 approved、non-stale Manifest。
      </p>
    )
  if (isLoading)
    return <p className="text-muted-foreground text-sm">正在查询精确 Manifest 收录状态…</p>

  return (
    <div className="flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium">精确 Manifest 收录</p>
          <p className="text-muted-foreground text-sm">
            当前采用 Manifest：
            {object.adoptedVersionId ? `ObjectVersion #${object.adoptedVersionId}` : "尚未冻结"}
          </p>
        </div>
        {work ? (
          <Badge variant="secondary">
            Work #{work.id} · {work.status}
          </Badge>
        ) : null}
      </div>
      <p className="text-muted-foreground text-sm">
        evaluate、freeze、Review 和收录统一在项目页交付面板完成；普通 adopted Deliverable
        不可直接收录。
      </p>
      {work ? (
        <Button
          nativeButton={false}
          render={<Link href="/studio/assets/works" />}
          variant="outline"
          className="w-fit"
        >
          <Library />
          查看作品与发布记录
        </Button>
      ) : null}
    </div>
  )
}
