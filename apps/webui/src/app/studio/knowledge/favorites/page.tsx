/**
 * /studio/knowledge/favorites——收藏夹
 * 按 targetType 分组：文档 / 作品 / 对话片段
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ExternalLink, Heart, Trash2 } from "lucide-react"
import Link from "next/link"
import { GlassCard, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type UserFavoriteVO,
  useRemoveFavorite,
  useUserFavorites
} from "@/lib/queries/use-user-favorites"

const TARGET_TYPE_LABELS: Record<string, string> = {
  DOC: "文档",
  ASSET: "作品",
  CONVERSATION: "对话片段",
  PROMPT: "提示词",
  PROJECT: "项目"
}

const TARGET_TYPE_HREF: Record<string, (id: number) => string> = {
  DOC: (id) => `/docs/${id}`,
  ASSET: (id) => `/studio/assets/works?highlight=${id}`,
  PROJECT: (id) => `/studio/projects/${id}`,
  PROMPT: (id) => `/studio/assets/prompts?highlight=${id}`
}

function FavoriteItem({ fav }: { fav: UserFavoriteVO }) {
  const remove = useRemoveFavorite()
  const href = TARGET_TYPE_HREF[fav.targetType]?.(fav.targetId)

  return (
    <GlassCard glow="none" className="border border-foreground/[0.06]">
      <div className="flex items-start gap-3 p-4">
        {fav.targetCoverUrl && (
          // biome-ignore lint/performance/noImgElement: 封面图
          <img
            src={fav.targetCoverUrl}
            alt=""
            className="size-12 shrink-0 rounded-lg object-cover"
          />
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-sm">
            {fav.targetTitle ??
              `${TARGET_TYPE_LABELS[fav.targetType] ?? fav.targetType} #${fav.targetId}`}
          </p>
          <div className="mt-1 flex items-center gap-2">
            <NeonChip tone="violet" size="sm">
              {TARGET_TYPE_LABELS[fav.targetType] ?? fav.targetType}
            </NeonChip>
            {fav.note && <p className="truncate text-muted-foreground text-xs">{fav.note}</p>}
          </div>
        </div>
        <div className="flex shrink-0 gap-1">
          {href && (
            <Link
              href={href}
              className="flex size-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-foreground/[0.04] hover:text-foreground"
            >
              <ExternalLink className="size-3.5" />
            </Link>
          )}
          <Button
            variant="ghost"
            size="icon-sm"
            className="size-7 text-destructive hover:text-destructive"
            onClick={() => remove.mutate(fav.id)}
          >
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      </div>
    </GlassCard>
  )
}

export default function StudioKnowledgeFavoritesPage() {
  const { data: page, isLoading } = useUserFavorites({ size: 50 })
  const favorites = page?.list ?? []

  // 按 targetType 分组
  const grouped = favorites.reduce<Record<string, UserFavoriteVO[]>>((acc, fav) => {
    const key = fav.targetType
    if (!acc[key]) acc[key] = []
    acc[key].push(fav)
    return acc
  }, {})

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <Heart className="size-5 text-rose-400" />
        <h1 className="font-semibold text-xl">我的收藏</h1>
      </header>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-xl" />
          ))}
        </div>
      ) : favorites.length === 0 ? (
        <div className="py-20 text-center text-muted-foreground text-sm">
          还没有收藏，去浏览内容吧
        </div>
      ) : (
        Object.entries(grouped).map(([type, items]) => (
          <div key={type} className="space-y-2">
            <p className="font-medium text-muted-foreground text-sm">
              {TARGET_TYPE_LABELS[type] ?? type}（{items.length}）
            </p>
            {items.map((fav) => (
              <FavoriteItem key={fav.id} fav={fav} />
            ))}
          </div>
        ))
      )}
    </div>
  )
}
