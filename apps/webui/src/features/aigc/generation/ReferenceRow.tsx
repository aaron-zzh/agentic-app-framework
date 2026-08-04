/**
 * 参考引用行——以自然语言句子形式展示引用素材
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useMediaDetails } from "@/lib/api/rest/media"
import { useAigcStore } from "../store"
import type { MediaVO } from "../types"

/** 素材 badge */
function AssetBadge({
  id,
  name,
  url,
  onRemove
}: {
  id: string
  name: string
  url: string
  onRemove: (id: string) => void
}) {
  return (
    <span className="inline-flex shrink-0 cursor-default items-center gap-1 rounded-md bg-muted px-1.5 py-0.5 align-middle text-foreground text-sm">
      {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
      <img src={url} alt={name} className="size-5 rounded-sm object-cover" />
      <span className="max-w-[120px] truncate text-xs">{name}</span>
      <button
        type="button"
        onClick={() => onRemove(id)}
        className="ml-0.5 rounded-full p-0.5 hover:bg-muted-foreground/20"
      >
        <X className="size-3" />
      </button>
    </span>
  )
}

function referenceDescription(media: MediaVO, index: number): string {
  try {
    const parsed: unknown = media.currentVersion.generationInfo
      ? JSON.parse(media.currentVersion.generationInfo)
      : null
    if (
      parsed !== null &&
      typeof parsed === "object" &&
      "referenceDesc" in parsed &&
      typeof parsed.referenceDesc === "string"
    ) {
      return parsed.referenceDesc
    }
  } catch {
    // 非法供应商元数据不影响参考素材展示。
  }
  return index === 0 ? "中的人物衣着发型及场景，调整" : "中的人物"
}

export function ReferenceRow() {
  const referenceMediaIds = useAigcStore((state) => state.referenceMediaIds)
  const uploadedReferenceDrafts = useAigcStore((state) => state.uploadedReferenceDrafts)
  const mediaQueries = useMediaDetails(referenceMediaIds)
  const media = mediaQueries.flatMap((query) => (query.data ? [query.data] : []))
  const removeReferenceMediaId = useAigcStore((state) => state.removeReferenceMediaId)
  const removeUploadedReferenceDraft = useAigcStore(
    (state) => state.removeUploadedReferenceDraft
  )

  if (media.length === 0 && uploadedReferenceDrafts.length === 0) return null

  return (
    <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-1 text-muted-foreground text-sm leading-loose">
      <span className="shrink-0">参考</span>
      {media.map((item, index) => (
        <span key={`media-${item.id}`} className="inline-flex flex-wrap items-baseline gap-x-1.5">
          <AssetBadge
            id={String(item.id)}
            name={item.name}
            url={item.currentVersion.thumbnailUrl ?? item.currentVersion.url}
            onRemove={(id) => removeReferenceMediaId(Number(id))}
          />
          <span className="text-foreground/70 text-sm">
            {referenceDescription(item, index)}
          </span>
        </span>
      ))}
      {uploadedReferenceDrafts.map((draft, index) => (
        <span key={`upload-${draft.key}`} className="inline-flex flex-wrap items-baseline gap-x-1.5">
          <AssetBadge
            id={draft.key}
            name={draft.name}
            url={draft.url}
            onRemove={removeUploadedReferenceDraft}
          />
          <span className="text-foreground/70 text-sm">
            {media.length + index === 0 ? "中的人物衣着发型及场景，调整" : "中的人物"}
          </span>
        </span>
      ))}
    </div>
  )
}
