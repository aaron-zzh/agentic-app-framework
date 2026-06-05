"use client"

import { m } from "framer-motion"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { AnimateBorder, transitionTap, varHover, varTap } from "@/components/animate"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/lib/auth/use-auth"
import { paths } from "@/lib/constants/paths"
import { useCreditGroups } from "@/lib/queries/use-credits"
import { CreditRechargeDialog } from "./CreditRechargeDialog"

const GROUP_ICON: Record<string, string> = {
  SUBSCRIPTION: "🎫",
  TOPUP: "💳",
  WEEKLY: "📅",
  REWARD: "🎁",
  MANUAL: "🎀"
}

interface Props {
  src?: string
  displayName?: string
  email?: string
  planName?: string
}

export function UserAvatarPopover({ src, displayName, email, planName = "Free" }: Props) {
  const [rechargeOpen, setRechargeOpen] = useState(false)
  const { data: groups, isLoading: groupsLoading } = useCreditGroups()
  const { logout } = useAuth()
  const router = useRouter()

  const totalCredits = groups?.reduce((sum, g) => sum + g.remain, 0) ?? 0

  async function handleLogout() {
    await logout()
    router.push("/login")
  }

  return (
    <>
      <Popover>
        <PopoverTrigger
          render={
            <m.button
              type="button"
              whileTap={varTap(0.96)}
              whileHover={varHover(1.04)}
              transition={transitionTap()}
              className="inline-flex items-center justify-center border-none bg-transparent p-0"
              aria-label="用户菜单"
            />
          }
        >
          <AnimateBorder rounded="full" borderWidth={1.5} size={40} glowSize={60} duration={8}>
            <Avatar className="!size-[36px] after:hidden">
              <AvatarImage
                src={src || "/assets/avatar/avatar.png"}
                alt={displayName || "用户头像"}
              />
              <AvatarFallback>{displayName?.charAt(0).toUpperCase() || "U"}</AvatarFallback>
            </Avatar>
          </AnimateBorder>
        </PopoverTrigger>

        <PopoverContent align="end" className="w-64 p-0" sideOffset={8}>
          {/* 用户信息 */}
          <div className="px-5 pt-5 pb-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="font-bold text-xl">{displayName || "User"}</span>
                <span className="rounded-full border px-2 py-0.5 text-muted-foreground text-xs">
                  {planName}
                </span>
              </div>
              <button
                type="button"
                className="flex items-center gap-1.5 text-sm hover:opacity-70"
                onClick={() => setRechargeOpen(true)}
                title="点击充值"
              >
                <span className="text-amber-400">🪙</span>
                <span className="font-medium">{totalCredits.toLocaleString()}</span>
                <span className="text-muted-foreground">|</span>
                <span className="text-muted-foreground">{planName}</span>
              </button>
            </div>
            {email && <p className="mt-0.5 text-muted-foreground text-sm">{email}</p>}

            {planName === "Free" && (
              <Button
                asChild
                className="mt-3 w-full rounded-full bg-gradient-to-r from-amber-500 to-orange-500 font-semibold text-white hover:opacity-90"
              >
                <Link href="/settings/pricing">▶ 开通会员</Link>
              </Button>
            )}
          </div>

          <div className="border-t" />

          {/* 积分分组明细 */}
          <div className="space-y-3 px-5 py-4">
            {groupsLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={`sk-${i}`} className="h-6 w-full" />
                ))}
              </div>
            ) : !groups?.length ? (
              [
                { batchType: "SUBSCRIPTION", label: "会员积分" },
                { batchType: "WEEKLY", label: "每周积分" },
                { batchType: "REWARD", label: "奖励积分" }
              ].map((g) => (
                <div key={g.batchType} className="flex items-center justify-between">
                  <span className="flex items-center gap-2 font-medium text-sm">
                    <span>{GROUP_ICON[g.batchType]}</span>
                    {g.label}
                  </span>
                  <span className="font-medium text-sm">0</span>
                </div>
              ))
            ) : (
              groups.map((group) => (
                <div key={group.batchType}>
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2 font-medium text-sm">
                      <span>{GROUP_ICON[group.batchType] ?? "🪙"}</span>
                      {group.label}
                    </span>
                    <span className="font-medium text-sm">{group.remain.toLocaleString()}</span>
                  </div>
                  {group.items?.map((item) => (
                    <div key={item.label} className="mt-0.5 flex items-center justify-between pl-6">
                      <span className="text-muted-foreground text-xs">{item.label}</span>
                      <span className="text-muted-foreground text-xs">
                        {item.remain.toLocaleString()}
                      </span>
                    </div>
                  ))}
                  {group.batchType === "WEEKLY" && (
                    <p className="mt-0.5 pl-6 text-muted-foreground text-xs">每周一 00:00 刷新</p>
                  )}
                </div>
              ))
            )}
          </div>

          {/* 查看用量 */}
          <div className="px-5 pb-4">
            <Button variant="secondary" className="w-full rounded-full" asChild>
              <Link href={paths.workspace.settingsProfile}>查看用量</Link>
            </Button>
          </div>

          <div className="border-t" />

          {/* 底部操作 */}
          <div className="space-y-1 px-5 py-3">
            <Button variant="ghost" className="w-full justify-start gap-3 rounded-lg" asChild>
              <Link href={paths.workspace.settingsProfile}>
                <span>⚙️</span> 管理账户
              </Link>
            </Button>
            <Button
              variant="ghost"
              className="w-full justify-start gap-3 rounded-lg text-destructive hover:text-destructive"
              onClick={handleLogout}
            >
              <span>🚪</span> 退出登录
            </Button>
          </div>
        </PopoverContent>
      </Popover>
      <CreditRechargeDialog open={rechargeOpen} onOpenChange={setRechargeOpen} />
    </>
  )
}
