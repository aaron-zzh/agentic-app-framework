/**
 * 邀请奖励视图——页面与拦截路由弹窗共享同一份 UI。
 *
 * 截图复刻：左卡注册奖励、右卡分销奖励、邀请链接 + 复制按钮、查看邀请历史。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Gift, Link2 } from "lucide-react"
import Link from "next/link"
import { useMemo } from "react"
import { toast } from "sonner"
import { AnimateBorder } from "@/components/animate/animate-border"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useInviteRewards, useMyInviteCode } from "@/lib/queries/use-invite"
import { cn } from "@/lib/utils/cn"

interface InviteRewardViewProps {
  /** 是否在弹窗中渲染——影响外层间距与宽度 */
  variant?: "page" | "dialog"
}

/** 把后端的 BigDecimal 比例（0.0500）格式化为百分比字符串 */
function formatRate(rate: string | number | undefined): string {
  if (rate == null) return "—"
  const v = typeof rate === "string" ? Number.parseFloat(rate) : rate
  if (Number.isNaN(v)) return "—"
  return `${(v * 100).toFixed(v * 100 < 10 ? 1 : 0).replace(/\.0$/, "")}%`
}

export function InviteRewardView({ variant = "page" }: InviteRewardViewProps) {
  const { data: inviteCode, isLoading: codeLoading } = useMyInviteCode()
  const { data: rewards, isLoading: rewardsLoading } = useInviteRewards()

  const inviteUrl = useMemo(() => {
    if (!inviteCode?.code) return ""
    const origin = typeof window === "undefined" ? "" : window.location.origin
    return `${origin}/?refCode=${inviteCode.code}`
  }, [inviteCode?.code])

  const handleCopy = async () => {
    if (!inviteUrl) return
    try {
      await navigator.clipboard.writeText(inviteUrl)
      toast.success("邀请链接已复制")
    } catch {
      toast.error("复制失败，请手动选择文本复制")
    }
  }

  return (
    <div className={cn("mx-auto", variant === "page" ? "max-w-3xl px-8 py-6" : "px-1 py-2")}>
      {/* 标题 */}
      <div className="mb-6 flex items-center justify-center gap-2 text-center">
        <Gift className="size-6 text-amber-400" aria-hidden />
        <h1 className="font-semibold text-2xl">邀请赚积分</h1>
      </div>

      {/* 两块奖励卡片 */}
      <div className="grid grid-cols-1 items-stretch gap-4 sm:grid-cols-[1fr_auto_1fr]">
        <RewardCard
          title=".. 邀请注册奖励 .."
          loading={rewardsLoading}
          enabled={rewards?.registerReward.enabled ?? true}
          highlight={
            rewards?.registerReward.creditAmount != null
              ? `+${rewards.registerReward.creditAmount}`
              : "+200"
          }
          unit="积分"
          description="好友通过你的邀请链接完成注册后发放。"
          footer={
            rewards?.registerReward.enabled
              ? `积分有效期 ${rewards.registerReward.expireDays} 天，最多可邀请 ${rewards.registerReward.maxInvites} 个用户。`
              : "积分有效期 7 天，最多可邀请 20 个用户。"
          }
        />

        <div className="hidden items-center justify-center font-semibold text-2xl text-muted-foreground sm:flex">
          +
        </div>

        <RewardCard
          title=".. 购买会员分销奖励 .."
          loading={rewardsLoading}
          enabled={rewards?.subscribeReward.enabled ?? true}
          highlight={`会员额度的 ${formatRate(rewards?.subscribeReward.level1Rate)}`}
          description="好友完成会员购买后，按月付会员额度计算，获得会员额度的对应比例的奖励积分。"
          footer={
            rewards?.subscribeReward.enabled
              ? `积分有效期 ${rewards.subscribeReward.frozenDays} 天，不限制邀请人数。`
              : "积分有效期 30 天，不限制邀请人数。"
          }
          highlightAsText
        />
      </div>

      {/* 邀请链接 + 复制 */}
      <div className="mt-6 flex items-center gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-full border bg-muted/40 px-3 py-2">
          <Link2 className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          {codeLoading ? (
            <Skeleton className="h-5 w-3/4" />
          ) : (
            <Input
              readOnly
              value={inviteUrl}
              className="h-auto border-0 bg-transparent p-0 text-sm shadow-none focus-visible:ring-0"
              aria-label="邀请链接"
            />
          )}
        </div>
        <AnimateBorder
          rounded="full"
          primaryColor="#f59e0b"
          secondaryColor="#ef4444"
          glowSize={50}
          duration={5}
        >
          <Button
            variant="default"
            className="rounded-full bg-linear-to-br from-amber-500 to-amber-700 px-6 font-medium text-white shadow-md hover:from-amber-400 hover:to-amber-600"
            disabled={!inviteUrl}
            onClick={handleCopy}
          >
            复制链接
          </Button>
        </AnimateBorder>
      </div>

      {/* 查看邀请历史 */}
      <div className="mt-6 border-t border-dashed pt-4 text-center">
        <Link
          href="/settings/invite/history"
          className={cn(
            buttonVariants({ variant: "link" }),
            "text-muted-foreground hover:text-foreground"
          )}
        >
          查看邀请历史
        </Link>
      </div>
    </div>
  )
}

interface RewardCardProps {
  title: string
  loading: boolean
  enabled: boolean
  highlight: string
  unit?: string
  description: string
  footer: string
  highlightAsText?: boolean
}

function RewardCard({
  title,
  loading,
  enabled,
  highlight,
  unit,
  description,
  footer,
  highlightAsText
}: RewardCardProps) {
  // 透明度由 enabled 控制
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 rounded-xl border border-amber-500/30 bg-gradient-to-b from-amber-500/5 to-transparent p-5",
        !enabled && "opacity-50"
      )}
    >
      <p className="text-primary/70 text-xs tracking-wider">{title}</p>
      {loading ? (
        <Skeleton className="h-10 w-32" />
      ) : highlightAsText ? (
        <p className="text-center font-bold text-2xl text-primary">{highlight}</p>
      ) : (
        <p className="font-bold text-primary">
          <span className="text-4xl tabular-nums">{highlight}</span>
          {unit && <span className="ml-1 text-base">{unit}</span>}
        </p>
      )}
      <p className="text-center text-muted-foreground text-xs leading-relaxed">{description}</p>
      <FooterTag text={footer} loading={loading} />
    </div>
  )
}

function FooterTag({ text, loading }: { text: string; loading: boolean }) {
  if (loading) return <Skeleton className="h-6 w-3/4" />
  return (
    <span className="rounded-md border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-primary/60 text-xs">
      {text}
    </span>
  )
}
