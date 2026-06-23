/**
 * /studio/me/outfits——助理装扮（商城 + 我的库存）
 * @author AaronZZH & Kiro
 */

"use client"

import { ShoppingBag, User } from "lucide-react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  type AvatarOutfitVO,
  type UserAvatarInventoryVO,
  useAvatarOutfits,
  useEquipOutfit,
  useMyAvatarInventory,
  usePurchaseOutfit
} from "@/lib/queries/use-avatar-outfits"
import { cn } from "@/lib/utils/index"

const RARITY_TONE: Record<string, "violet" | "cyan" | "emerald" | "amber"> = {
  COMMON: "emerald",
  RARE: "cyan",
  EPIC: "violet",
  LEGENDARY: "amber"
}

function OutfitShopCard({ outfit }: { outfit: AvatarOutfitVO }) {
  const purchase = usePurchaseOutfit()

  return (
    <GlassCard glow="none" className="overflow-hidden border border-foreground/[0.06]">
      <div className="flex aspect-square items-center justify-center bg-foreground/[0.04]">
        {outfit.thumbnailUrl ? (
          // biome-ignore lint/performance/noImgElement: 装扮图
          <img src={outfit.thumbnailUrl} alt={outfit.name} className="size-full object-cover" />
        ) : (
          <User className="size-10 text-muted-foreground/30" />
        )}
      </div>
      <div className="space-y-2 p-3">
        <p className="truncate font-medium text-sm">{outfit.name}</p>
        <div className="flex items-center justify-between">
          <NeonChip tone={RARITY_TONE[outfit.rarity] ?? "violet"} size="sm">
            {outfit.rarity}
          </NeonChip>
          {outfit.owned ? (
            <span className="text-emerald-400 text-xs">已拥有</span>
          ) : outfit.price ? (
            <GlowButton
              tone="primary"
              size="sm"
              onClick={() =>
                purchase.mutate(outfit.id, {
                  onSuccess: () => toast.success(`已购买「${outfit.name}」`),
                  onError: (err) =>
                    toast.error(`购买失败：${err instanceof Error ? err.message : "未知错误"}`)
                })
              }
              disabled={purchase.isPending}
            >
              {outfit.price} 积分
            </GlowButton>
          ) : (
            <span className="text-muted-foreground text-xs">免费</span>
          )}
        </div>
      </div>
    </GlassCard>
  )
}

function InventoryCard({ item }: { item: UserAvatarInventoryVO }) {
  const equip = useEquipOutfit()

  return (
    <GlassCard
      glow="none"
      className={cn(
        "overflow-hidden border",
        item.equipped ? "border-violet-400/40" : "border-foreground/[0.06]"
      )}
    >
      <div className="flex aspect-square items-center justify-center bg-foreground/[0.04]">
        {item.outfit.thumbnailUrl ? (
          // biome-ignore lint/performance/noImgElement: 装扮图
          <img
            src={item.outfit.thumbnailUrl}
            alt={item.outfit.name}
            className="size-full object-cover"
          />
        ) : (
          <User className="size-10 text-muted-foreground/30" />
        )}
      </div>
      <div className="space-y-2 p-3">
        <p className="truncate font-medium text-sm">{item.outfit.name}</p>
        <div className="flex items-center justify-between">
          {item.equipped ? (
            <NeonChip tone="violet" size="sm" dot>
              装备中
            </NeonChip>
          ) : (
            <GlowButton
              tone="ghost"
              size="sm"
              onClick={() =>
                equip.mutate(
                  { outfitId: item.outfitId },
                  {
                    onSuccess: () => toast.success(`已装备「${item.outfit.name}」`),
                    onError: (err) =>
                      toast.error(`装备失败：${err instanceof Error ? err.message : "未知错误"}`)
                  }
                )
              }
              disabled={equip.isPending}
            >
              装备
            </GlowButton>
          )}
        </div>
      </div>
    </GlassCard>
  )
}

export default function StudioMeOutfitsPage() {
  const { data: shopPage, isLoading: shopLoading } = useAvatarOutfits({ size: 20 })
  const { data: inventory, isLoading: invLoading } = useMyAvatarInventory()

  const shopItems = shopPage?.list ?? []
  const myItems = inventory ?? []

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <ShoppingBag className="size-5 text-violet-400" />
        <h1 className="font-semibold text-xl">助理装扮</h1>
      </header>

      <Tabs defaultValue="shop">
        <TabsList>
          <TabsTrigger value="shop">装扮商城</TabsTrigger>
          <TabsTrigger value="my">我的装扮</TabsTrigger>
        </TabsList>

        <TabsContent value="shop" className="mt-4">
          {shopLoading ? (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {Array.from({ length: 10 }).map((_, i) => (
                <Skeleton key={i} className="aspect-square rounded-xl" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {shopItems.map((item) => (
                <OutfitShopCard key={item.id} outfit={item} />
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="my" className="mt-4">
          {invLoading ? (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="aspect-square rounded-xl" />
              ))}
            </div>
          ) : myItems.length === 0 ? (
            <div className="py-20 text-center text-muted-foreground text-sm">
              还没有装扮，去商城看看吧
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {myItems.map((item) => (
                <InventoryCard key={item.id} item={item} />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
