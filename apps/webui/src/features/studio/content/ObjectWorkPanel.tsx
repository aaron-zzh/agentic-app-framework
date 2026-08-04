/**
 * adopted Deliverable 到 AigcWork 的显式收录入口。
 * @author AaronZZH & Kiro
 */

"use client"

import { Library, Plus } from "lucide-react"
import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcWorks, useCollectAigcWork } from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

interface ObjectWorkPanelProps {
  project: AigcProject
  object: AigcProjectObject
}

export function ObjectWorkPanel({ project, object }: ObjectWorkPanelProps) {
  const { data, isLoading } = useAigcWorks({ projectId: project.id })
  const collect = useCollectAigcWork()
  const work = data?.list.find(
    (item) =>
      item.deliverableObjectId === object.id &&
      item.adoptedObjectVersionId === object.adoptedVersionId
  )
  const isDeliverable =
    object.objectType === "deliverable_set" || object.objectType.endsWith("_deliverable")
  const canCollect =
    project.status === "delivering" && isDeliverable && object.adoptedVersionId !== undefined

  if (!isDeliverable) {
    return <p className="text-muted-foreground text-sm">只有 Deliverable 对象可收录到作品库。</p>
  }

  if (isLoading) return <p className="text-muted-foreground text-sm">正在查询作品收录状态…</p>

  return (
    <div className="flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-medium">作品收录</p>
          <p className="text-muted-foreground text-sm">
            Work 只引用当前 adopted ObjectVersion，不复制正文或媒体文件。
          </p>
        </div>
        {work ? <Badge variant="secondary">{work.status}</Badge> : null}
      </div>

      {work ? (
        <Button
          nativeButton={false}
          render={<Link href="/studio/assets/works" />}
          variant="outline"
        >
          <Library /> 查看作品与发布记录
        </Button>
      ) : canCollect ? (
        <Button
          type="button"
          disabled={collect.isPending}
          onClick={() =>
            collect.mutate(
              {
                projectId: project.id,
                deliverableObjectId: object.id,
                adoptedObjectVersionId: object.adoptedVersionId as number,
                visibility: "PRIVATE"
              },
              { onSuccess: () => notify.success("交付物已收录为作品") }
            )
          }
        >
          <Plus /> {collect.isPending ? "正在收录…" : "收录到作品库"}
        </Button>
      ) : (
        <p className="text-muted-foreground text-sm">
          {object.adoptedVersionId === undefined
            ? "请先采用一个对象版本。"
            : project.status === "completed"
              ? "项目已完成；当前后端契约不允许完成后新增 Work。"
              : "项目审核通过进入交付阶段后，才可收录 Work。"}
        </p>
      )}
    </div>
  )
}
