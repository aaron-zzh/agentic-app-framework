/**
 * BankingInviteFriends——邀请好友横幅
 */

"use client"

import { Button } from "@/components/ui/button"

interface BankingInviteFriendsProps {
  title?: string
  price?: string
  description?: string
}

export function BankingInviteFriends({ title, price, description }: BankingInviteFriendsProps) {
  return (
    <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-primary to-primary/80 p-8 text-primary-foreground">
      {/* 装饰圆形 */}
      <div className="absolute -top-6 -right-6 h-32 w-32 rounded-full bg-white/10" />
      <div className="absolute top-8 right-8 h-16 w-16 rounded-full bg-white/10" />

      <p className="mb-1 whitespace-pre-line font-semibold text-lg leading-snug">{title}</p>
      <p className="mb-2 font-bold text-4xl">{price}</p>
      <p className="mb-6 text-primary-foreground/70 text-sm">{description}</p>

      <div className="flex items-center gap-2 rounded-lg bg-black/20 px-3 py-1">
        <input
          placeholder="Email"
          className="flex-1 bg-transparent text-primary-foreground text-sm outline-none placeholder:text-primary-foreground/50"
        />
        <Button size="sm" variant="secondary" className="shrink-0">
          Invite
        </Button>
      </div>
    </div>
  )
}
