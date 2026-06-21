"use client"

import { Coins, Gift, Globe, LogOut, MessageSquare, Settings, Star, Ticket } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useLocale, useTranslations } from "next-intl"
import { useState, useTransition } from "react"
import { AnimateBorder } from "@/components/animate"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Skeleton } from "@/components/ui/skeleton"
import { type Locale, locales } from "@/i18n/config"
import { setUserLocale } from "@/i18n/locale"
import { useAuth } from "@/lib/auth/use-auth"
import { paths } from "@/lib/constants/paths"
import { useCurrentSubscription } from "@/lib/queries/use-billing-plans"
import { useCreditGroups } from "@/lib/queries/use-credits"
import { $url } from "@/lib/utils"
import { cn } from "@/lib/utils/cn"
import { CreditRechargeDialog } from "./CreditRechargeDialog"

const POPOVER_TREE = [
  {
    key: "MEMBER",
    icon: <Ticket className="size-3.5" />,
    children: ["SUBSCRIPTION", "TOPUP", "MANUAL"] as const
  },
  {
    key: "WEEKLY",
    icon: <Star className="size-3.5" />,
    children: [] as const
  },
  {
    key: "REWARD",
    icon: <Gift className="size-3.5" />,
    children: [] as const
  }
]

export function UserAvatarPopover() {
  const [open, setOpen] = useState(false)
  const [rechargeOpen, setRechargeOpen] = useState(false)
  const { data: groups, isLoading: groupsLoading } = useCreditGroups()
  const { data: subscription } = useCurrentSubscription()
  const { logout, user } = useAuth()
  const router = useRouter()
  const t = useTranslations("userAvatarPopover")

  const displayName = user?.nickname || user?.username || "User"
  const email = user?.email
  const src = user?.avatar
  const planName = subscription?.planName ?? "Free"
  const totalCredits = groups?.reduce((sum, g) => sum + g.remain, 0) ?? 0

  async function handleLogout() {
    await logout()
    router.push("/login")
  }

  return (
    <>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          render={
            <button
              type="button"
              className="inline-flex cursor-pointer items-center justify-center border-none bg-transparent p-0"
              aria-label="用户菜单"
            />
          }
        >
          <AnimateBorder rounded="full" borderWidth={1.5} size={40} glowSize={60} duration={8}>
            <Avatar className="!size-[36px] after:hidden">
              <AvatarImage
                src={src || $url.cdn("/assets/avatar/avatar.png")}
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
                <span className="font-bold text-foreground text-xl">{displayName || "User"}</span>
                <span className="rounded-full border px-2 py-0.5 text-muted-foreground text-xs">
                  {planName}
                </span>
              </div>
              <button
                type="button"
                className="flex items-center gap-1.5 text-sidebar-foreground text-sm hover:opacity-70"
                onClick={() => setRechargeOpen(true)}
                title={t("rechargeTitle")}
              >
                <Coins className="size-4 text-amber-400" />
                <span className="font-medium text-foreground">{totalCredits.toLocaleString()}</span>
              </button>
            </div>
            {email && <p className="mt-0.5 text-muted-foreground text-sm">{email}</p>}

            {planName === "Free" && (
              <Button
                nativeButton={false}
                render={<Link href={paths.workspace.settingsPricing} />}
                onClick={() => setOpen(false)}
                className="mt-3 w-full rounded-full bg-gradient-to-r from-amber-500 to-orange-500 font-semibold text-white hover:opacity-90"
              >
                {t("upgradeButton")}
              </Button>
            )}
          </div>

          <div className="border-t" />

          {/* 积分分组明细 */}
          <div className="space-y-2 px-5 py-4">
            {groupsLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={`sk-${i}`} className="h-6 w-full" />
                ))}
              </div>
            ) : (
              POPOVER_TREE.map((group) => {
                const remainMap: Record<string, number> = {}
                for (const g of groups ?? []) remainMap[g.batchType] = g.remain
                const parentRemain =
                  group.children.length > 0
                    ? group.children.reduce((sum, k) => sum + (remainMap[k] ?? 0), 0)
                    : (remainMap[group.key] ?? 0)
                return (
                  <div key={group.key}>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 font-medium text-sidebar-foreground text-sm">
                        {group.icon}
                        {t(`creditGroups.${group.key}` as Parameters<typeof t>[0])}
                      </span>
                      <span className="font-medium text-foreground text-sm">
                        {parentRemain.toLocaleString()}
                      </span>
                    </div>
                    {group.children.map((childKey) => (
                      <div key={childKey} className="flex items-center justify-between pl-4">
                        <span className="text-muted-foreground text-xs">
                          {t(`creditChildren.${childKey}` as Parameters<typeof t>[0])}
                        </span>
                        <span className="text-muted-foreground text-xs">
                          {(remainMap[childKey] ?? 0).toLocaleString()}
                        </span>
                      </div>
                    ))}
                    {group.key === "WEEKLY" && (
                      <p className="pl-4 text-muted-foreground text-xs">{t("weeklyRefresh")}</p>
                    )}
                  </div>
                )
              })
            )}
          </div>

          {/* 查看用量 */}
          <div className="px-5 pb-4">
            <Button
              variant="secondary"
              className="w-full rounded-full"
              nativeButton={false}
              render={<Link href={paths.workspace.settingsCredits} />}
              onClick={() => setOpen(false)}
            >
              {t("viewUsage")}
            </Button>
          </div>

          <div className="border-t" />

          {/* 底部操作 */}
          <div className="space-y-1 px-5 py-3">
            <Button
              variant="ghost"
              className="w-full justify-start gap-3 rounded-lg text-sidebar-foreground hover:text-foreground"
              nativeButton={false}
              render={<Link href={paths.workspace.settingsProfile} />}
            >
              <Settings className="size-4" /> {t("manageAccount")}
            </Button>
            <Button
              variant="ghost"
              className="w-full justify-start gap-3 rounded-lg text-sidebar-foreground hover:text-foreground"
              nativeButton={false}
              render={<Link href={paths.feedback} target="_blank" />}
            >
              <MessageSquare className="size-4" /> {t("feedback")}
            </Button>
            <LocaleSwitchRow />
            <Button
              variant="ghost"
              className="w-full justify-start gap-3 rounded-lg text-destructive hover:text-destructive"
              onClick={handleLogout}
            >
              <LogOut className="size-4" /> {t("logout")}
            </Button>
          </div>
        </PopoverContent>
      </Popover>
      <CreditRechargeDialog open={rechargeOpen} onOpenChange={setRechargeOpen} />
    </>
  )
}

/** 语言切换行：图标 + 标签 + 横向 segment 控件（zh/en 当前语言高亮） */
const LOCALE_SHORT: Record<Locale, string> = {
  zh: "中",
  en: "EN"
}

function LocaleSwitchRow() {
  const currentLocale = useLocale() as Locale
  const router = useRouter()
  const [isPending, startTransition] = useTransition()
  const t = useTranslations("userAvatarPopover")

  function switchLocale(locale: Locale) {
    if (locale === currentLocale || isPending) return
    startTransition(async () => {
      await setUserLocale(locale)
      router.refresh()
    })
  }

  return (
    <div className="flex items-center justify-between gap-3 rounded-lg px-3 py-1.5">
      <span className="flex items-center gap-3 text-sidebar-foreground text-sm">
        <Globe className="size-4" />
        {t("language")}
      </span>
      <div className="flex items-center gap-0.5 rounded-md bg-muted p-0.5">
        {locales.map((l) => (
          <button
            key={l}
            type="button"
            onClick={() => switchLocale(l)}
            disabled={isPending}
            aria-pressed={l === currentLocale}
            className={cn(
              "rounded px-2 py-0.5 text-xs transition-colors disabled:opacity-50",
              l === currentLocale
                ? "bg-background font-medium shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {LOCALE_SHORT[l]}
          </button>
        ))}
      </div>
    </div>
  )
}
