/**
 * 积分余额面板
 * @author AaronZZH & Kiro
 */

"use client"

import { Coins, RefreshCw } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { useCreditBalance } from "@/lib/queries/use-credits"

export function CreditsPanel() {
  const { data, isLoading, refetch, isFetching } = useCreditBalance()
  const [spinning, setSpinning] = useState(false)

  const handleRefresh = async () => {
    setSpinning(true)
    await refetch()
    setSpinning(false)
  }

  return (
    <div className="space-y-3">
      <div className="text-center">
        {isLoading ? (
          <Skeleton className="mx-auto h-9 w-24" />
        ) : (
          <div className="flex items-center justify-center gap-2">
            <Coins className="size-5 text-amber-400" />
            <span className="font-bold text-3xl">{data?.balance ?? 0}</span>
            <button
              type="button"
              onClick={handleRefresh}
              disabled={isFetching}
              aria-label="刷新积分余额"
              className="rounded p-1 text-muted-foreground transition-colors hover:bg-foreground/[0.06] hover:text-foreground disabled:opacity-50"
            >
              <RefreshCw className={`size-3.5 ${spinning || isFetching ? "animate-spin" : ""}`} />
            </button>
          </div>
        )}
        <p className="mt-1 text-muted-foreground text-xs">当前积分余额</p>
      </div>
      <div className="flex flex-col gap-1.5">
        <Link
          href="/studio/me/credits"
          className="rounded border border-foreground/8 bg-foreground/2 px-2 py-1.5 text-center text-xs transition-colors hover:bg-foreground/[0.06]"
        >
          充值积分
        </Link>
        <Link
          href="/studio/me/credits"
          className="rounded border border-foreground/8 bg-foreground/2 px-2 py-1.5 text-center text-xs transition-colors hover:bg-foreground/[0.06]"
        >
          消费明细
        </Link>
      </div>
    </div>
  )
}
