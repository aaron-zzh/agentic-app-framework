/**
 * 积分余额面板
 * @author AaronZZH & Kiro
 */

"use client"

import { Coins } from "lucide-react"
import Link from "next/link"
import { Skeleton } from "@/components/ui/skeleton"
import { useCreditBalance } from "@/lib/queries/use-credits"

export function CreditsPanel() {
  const { data, isLoading } = useCreditBalance()

  return (
    <div className="space-y-3">
      <div className="text-center">
        {isLoading ? (
          <Skeleton className="mx-auto h-9 w-24" />
        ) : (
          <div className="flex items-center justify-center gap-2">
            <Coins className="size-5 text-amber-400" />
            <span className="font-bold text-3xl">{data?.balance ?? 0}</span>
          </div>
        )}
        <p className="mt-1 text-muted-foreground text-xs">当前积分余额</p>
      </div>
      <div className="flex flex-col gap-1.5">
        <Link
          href="/studio/me/credits"
          className="rounded border border-foreground/[0.08] bg-foreground/[0.02] px-2 py-1.5 text-center text-xs transition-colors hover:bg-foreground/[0.06]"
        >
          充值积分
        </Link>
        <Link
          href="/studio/me/credits"
          className="rounded border border-foreground/[0.08] bg-foreground/[0.02] px-2 py-1.5 text-center text-xs transition-colors hover:bg-foreground/[0.06]"
        >
          消费明细
        </Link>
      </div>
    </div>
  )
}
