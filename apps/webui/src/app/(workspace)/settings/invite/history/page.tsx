/**
 * 邀请历史子页 /settings/invite/history
 *
 * 列表展示：被邀请人头像/昵称、注册时间、是否会员、获得的奖励积分。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { ArrowLeft } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button, buttonVariants } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { useMyInviteHistory } from "@/lib/queries/use-invite"
import { cn } from "@/lib/utils/cn"

const PAGE_SIZE = 20

export default function InviteHistoryPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMyInviteHistory(page, PAGE_SIZE)
  const list = data?.list ?? []
  const total = data?.total ?? 0
  const hasNext = (page + 1) * PAGE_SIZE < total

  return (
    <div className="mx-auto max-w-3xl px-8 py-6">
      <div className="mb-4 flex items-center gap-2">
        <Link
          href="/settings/invite"
          className={cn(buttonVariants({ variant: "ghost", size: "icon" }))}
          aria-label="返回邀请奖励"
        >
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="font-semibold text-xl">邀请历史</h1>
        <span className="ml-auto text-muted-foreground text-sm">共 {total} 位好友</span>
      </div>

      <Separator />

      {isLoading ? (
        <div className="space-y-3 pt-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={`row-sk-${i}`} className="h-14 w-full" />
          ))}
        </div>
      ) : list.length === 0 ? (
        <p className="py-12 text-center text-muted-foreground text-sm">
          还没有好友通过你的邀请注册，去复制链接邀请吧～
        </p>
      ) : (
        <div className="divide-y">
          {list.map((row) => (
            <InvitedUserRow key={row.contactId} row={row} />
          ))}
        </div>
      )}

      {/* 分页 */}
      {(page > 0 || hasNext) && (
        <div className="flex justify-center gap-2 pt-4">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            上一页
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={!hasNext}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      )}
    </div>
  )
}

function InvitedUserRow({
  row
}: {
  row: {
    contactId: number
    nickname: string | null
    avatar: string | null
    registerTime: string
    isMember: boolean
    rewardCredits: number
  }
}) {
  const displayName = row.nickname?.trim() || `好友 ${row.contactId}`
  const initial = displayName.slice(0, 1).toUpperCase()
  return (
    <div className="flex items-center gap-3 py-3">
      <Avatar className="size-10">
        {row.avatar && <AvatarImage src={row.avatar} alt={displayName} />}
        <AvatarFallback>{initial}</AvatarFallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate font-medium">{displayName}</p>
          {row.isMember && (
            <span className="rounded-md bg-amber-500/10 px-1.5 py-0.5 text-amber-500 text-xs">
              会员
            </span>
          )}
        </div>
        <p className="mt-0.5 text-muted-foreground text-xs">
          注册于 {format(new Date(row.registerTime), "yyyy年MM月dd日 HH:mm", { locale: zhCN })}
        </p>
      </div>
      <div className="text-right">
        <p
          className={cn(
            "font-semibold tabular-nums",
            row.rewardCredits > 0 ? "text-emerald-500" : "text-muted-foreground"
          )}
        >
          {row.rewardCredits > 0 ? `+${row.rewardCredits}` : "—"}
        </p>
        <p className="text-muted-foreground text-xs">奖励积分</p>
      </div>
    </div>
  )
}
