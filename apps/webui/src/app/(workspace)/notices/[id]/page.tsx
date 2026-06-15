/**
 * 公告详情页
 * @author AaronZZH
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { ArrowLeft } from "lucide-react"
import { useRouter } from "next/navigation"
import { use } from "react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { request } from "@/lib/api/rest/entity/crud"

interface NoticeVO {
  id: number
  title: string
  content: string
  type: string
  status: number
  publishTime: string
  createTime: string
}

function useNotice(id: string) {
  return useQuery({
    queryKey: ["notice", id],
    queryFn: () => request<NoticeVO>(`/system/notices/${id}`)
  })
}

const TYPE_LABEL: Record<string, string> = {
  NOTICE: "通知",
  ANNOUNCEMENT: "公告"
}

export default function NoticePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const { data: notice, isLoading } = useNotice(id)

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-8 py-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (!notice) {
    return (
      <div className="mx-auto max-w-3xl px-8 py-6">
        <p className="text-muted-foreground text-sm">公告不存在或已删除</p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl px-8 py-6">
      <Button variant="ghost" size="sm" className="mb-4 -ml-2" onClick={() => router.back()}>
        <ArrowLeft className="mr-1 size-4" />
        返回
      </Button>

      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <span className="rounded bg-primary/10 px-2 py-0.5 text-primary text-xs">
            {TYPE_LABEL[notice.type] ?? notice.type}
          </span>
        </div>
        <h1 className="font-semibold text-2xl">{notice.title}</h1>
        <p className="text-muted-foreground text-sm">
          发布于{" "}
          {format(new Date(notice.publishTime ?? notice.createTime), "yyyy年MM月dd日 HH:mm", {
            locale: zhCN
          })}
        </p>
      </div>

      <Separator className="my-6" />

      <div className="prose prose-sm max-w-none whitespace-pre-wrap text-sm leading-relaxed">
        {notice.content}
      </div>
    </div>
  )
}
