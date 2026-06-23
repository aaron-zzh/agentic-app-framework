/**
 * /studio/me/pricing——模型能力 & 收费标准（积分计价，按类型筛选）
 */

"use client"

import { RefreshCw, Search, Zap } from "lucide-react"
import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import type { PublicModelPricingVO } from "@/lib/api/rest/ai/ai-model"
import { useModelPricing } from "@/lib/queries/use-ai-models"
import { cn } from "@/lib/utils/index"

const CAP_LABEL: Record<string, string> = {
  IMAGE_GEN: "图像生成", VIDEO_GEN: "视频生成", CHAT: "文本对话",
  AUDIO: "音频", EMBEDDING: "向量", SPEECH_ASR: "语音识别",
  SPEECH_TTS: "语音合成", MUSIC_GEN: "音乐生成", VISION: "识图",
  RERANK: "重排序", OCR: "文字识别"
}

type ModelKind = "全部" | "文本" | "图像" | "视频" | "音频" | "检索"

function toKind(caps: string): Exclude<ModelKind, "全部"> {
  if (caps.includes("IMAGE_GEN")) return "图像"
  if (caps.includes("VIDEO_GEN")) return "视频"
  if (caps.includes("AUDIO") || caps.includes("SPEECH") || caps.includes("MUSIC")) return "音频"
  if (caps.includes("EMBEDDING") || caps.includes("RERANK")) return "检索"
  return "文本"
}

/** 简化价格：只显示 X 积分/单位，去掉输出和 token 细节 */
function formatCredits(m: PublicModelPricingVO): string {
  switch (m.quotaType) {
    case 1: return m.creditPerUse != null ? `${m.creditPerUse} 积分/次` : "—"
    case 2: return m.creditPerSec != null ? `${m.creditPerSec} 积分/秒` : "—"
    case 3: return m.creditPerUnit != null ? `${m.creditPerUnit} 积分/张` : "按规格"
    default:
      if (m.inputCreditPerK != null) return `${m.inputCreditPerK} 积分/千 Token`
      return "—"
  }
}

function ModelCard({ model }: { model: PublicModelPricingVO }) {
  const caps = (model.capabilities ?? "CHAT").split(",").map(s => s.trim())
  const kind = toKind(model.capabilities ?? "")
  const price = formatCredits(model)

  return (
    <article className="flex flex-col gap-3 rounded-xl border bg-card p-4 transition-all hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-start justify-between gap-2">
        <p className="font-semibold text-sm leading-snug">{model.displayName}</p>
        <Badge variant="outline" className="shrink-0 text-xs">{kind}</Badge>
      </div>

      <div className="flex flex-wrap gap-1">
        {caps.slice(0, 3).map(c => (
          <span key={c} className="rounded-md bg-foreground/[0.05] px-1.5 py-0.5 text-[10px] text-muted-foreground">
            {CAP_LABEL[c] ?? c}
          </span>
        ))}
      </div>

      <div className="mt-auto rounded-lg bg-amber-400/[0.08] px-3 py-2">
        <p className="font-medium text-amber-400 text-xs">{price}</p>
      </div>
    </article>
  )
}

export default function StudioMePricingPage() {
  const { data: models = [], isLoading } = useModelPricing()
  const [search, setSearch] = useState("")
  const [kind, setKind] = useState<ModelKind>("全部")

  const kindCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const m of models) counts.set(toKind(m.capabilities ?? ""), (counts.get(toKind(m.capabilities ?? "")) ?? 0) + 1)
    return [
      { label: "全部", count: models.length },
      ...["文本", "图像", "视频", "音频", "检索"].map(l => ({ label: l, count: counts.get(l) ?? 0 })).filter(i => i.count > 0)
    ] as { label: ModelKind; count: number }[]
  }, [models])

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase()
    return models.filter(m => {
      const matchKind = kind === "全部" || toKind(m.capabilities ?? "") === kind
      const matchSearch = !kw || m.displayName.toLowerCase().includes(kw)
      return matchKind && matchSearch
    })
  }, [models, kind, search])

  return (
    <div className="flex h-full">
      {/* 左侧类型筛选 */}
      <aside className="w-40 shrink-0 overflow-y-auto border-r p-4 space-y-1">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold text-sm">类型</h2>
          <Button variant="ghost" size="icon" className="size-6" onClick={() => { setKind("全部"); setSearch("") }}>
            <RefreshCw className="size-3" />
          </Button>
        </div>
        {kindCounts.map(item => (
          <button
            key={item.label}
            type="button"
            onClick={() => setKind(item.label)}
            className={cn(
              "flex h-8 w-full items-center justify-between rounded-lg px-2.5 text-sm transition-colors",
              kind === item.label
                ? "bg-primary/10 font-medium text-primary"
                : "text-muted-foreground hover:bg-foreground/[0.04] hover:text-foreground"
            )}
          >
            <span>{item.label}</span>
            <span className="rounded-full bg-foreground/[0.06] px-1.5 py-0.5 text-[10px]">{item.count}</span>
          </button>
        ))}
      </aside>

      {/* 右侧内容 */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <div className="flex items-center gap-3 border-b px-5 py-3">
          <Zap className="size-4 text-violet-400" />
          <h1 className="font-semibold text-sm">模型能力 &amp; 收费标准</h1>
          <Badge variant="outline" className="ml-auto text-xs">{filtered.length} 个</Badge>
        </div>
        <div className="px-5 pt-3">
          <div className="relative">
            <Search className="absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索模型..." className="h-8 pl-8 text-sm" />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-3">
          {isLoading ? (
            <div className="grid gap-3 [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
              {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-xl" />)}
            </div>
          ) : filtered.length === 0 ? (
            <p className="py-16 text-center text-muted-foreground text-sm">暂无匹配模型</p>
          ) : (
            <div className="grid gap-3 [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
              {filtered.map(m => <ModelCard key={m.modelId} model={m} />)}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
