"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  ChevronDown,
  Copy,
  Grid2X2,
  Pencil,
  RefreshCw,
  Search,
  Sparkles,
  Table2
} from "lucide-react"
import { useMemo, useRef, useState } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"
import * as $url from "@/lib/utils/asset-url"
import { cn } from "@/lib/utils/cn"

type ModelKind = "文本" | "图像" | "音视频" | "检索"
type BillingKind = "按量计费" | "按次计费"
type PriceUnit = "M tokens" | "万字符" | "分钟" | "秒" | "次"

interface ModelCard {
  id: string
  /** 后端数据库主键（用于编辑/更新接口） */
  numericId: number
  name: string
  modelName: string
  provider: string
  providerLabel: string
  providerTone: "blue" | "orange" | "green" | "slate" | "violet" | "rose"
  kind: ModelKind
  tags: string[]
  description: string
  inputPrice?: number
  outputPrice?: number
  cachePrice?: number
  modelPrice?: number
  /** 后端原始 modelPrice（元），用于编辑表单回填，未经换算 */
  rawModelPrice?: number
  /** 后端原始 inputPricePerK / outputPricePerK（元/千 token），用于编辑表单回填 */
  rawInputPricePerK?: number
  rawOutputPricePerK?: number
  /** 计费类型：0=按量 1=按次 2=按秒 3=按单元 */
  quotaType: number
  /** 价格展示单位（如 "M tokens" / "万字符" / "分钟" / "次"），byVariant=true 时不展示具体单价 */
  priceUnit: PriceUnit
  /** 是否按规格计费（如视频生成有多档价格矩阵）。true 时仅显示"按规格计费"。 */
  byVariant: boolean
  billing: BillingKind
  enabled: boolean
}

interface BackendAiModel {
  id: number
  modelId: string
  displayName: string
  provider: string
  providerType: string
  modelName: string
  inputPricePerK?: number | null
  outputPricePerK?: number | null
  modelPrice?: number | null
  quotaType?: number | null
  capabilities?: string | null
  enabled: boolean
}

interface ImportResult {
  createdCount: number
  updatedCount: number
  selectedCount: number
}

const providerTones: Record<ModelCard["providerTone"], string> = {
  blue: "bg-blue-50 text-blue-700 ring-blue-200",
  orange: "bg-orange-50 text-orange-700 ring-orange-200",
  green: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  slate: "bg-slate-100 text-slate-700 ring-slate-200",
  violet: "bg-violet-50 text-violet-700 ring-violet-200",
  rose: "bg-rose-50 text-rose-700 ring-rose-200"
}

function toModelKind(capabilities?: string | null): ModelKind {
  if (!capabilities) return "文本"
  if (capabilities.includes("IMAGE_GEN")) return "图像"
  if (capabilities.includes("AUDIO")) return "音视频"
  if (capabilities.includes("EMBEDDING") || capabilities.includes("RERANK")) return "检索"
  return "文本"
}

function providerLabel(provider: string): string {
  const map: Record<string, string> = {
    openai: "OpenAI",
    anthropic: "Anthropic",
    google: "Google",
    aliyun: "阿里百炼",
    qwen: "阿里百炼",
    volcengine: "火山方舟",
    deepseek: "DeepSeek",
    third_party: "第三方聚合"
  }
  return map[provider] ?? provider
}

function providerTone(provider: string): ModelCard["providerTone"] {
  if (provider.includes("anthropic")) return "orange"
  if (provider.includes("google") || provider.includes("deepseek")) return "blue"
  if (provider.includes("aliyun") || provider.includes("qwen")) return "violet"
  if (provider.includes("pixverse")) return "rose"
  if (provider.includes("xai")) return "green"
  return "slate"
}

/**
 * 根据 capabilities + quotaType 决定价格展示单位与换算系数。
 *
 * 后端字段语义：
 * - inputPricePerK / outputPricePerK：CHAT/EMBEDDING 时是 元/千 token；SPEECH_TTS 时是 元/千字符
 * - modelPrice：按次（quotaType=1）/按秒（quotaType=2）固定单价（元）
 *
 * 前端统一展示为人民币（¥），单位按能力路由：
 * - VIDEO_GEN（quotaType=3 或包含 VIDEO_GEN 能力）→ 按规格计费，不展示具体单价
 * - IMAGE_GEN / 3D（quotaType=1）→ ¥X/次
 * - SPEECH_TTS → ¥X/万字符（×10，元/千字符 → 元/万字符）
 * - SPEECH_ASR → ¥X/分钟（×60，元/秒 → 元/分钟）
 * - MUSIC_GEN → ¥X/秒（modelPrice 原值）
 * - CHAT/EMBEDDING/RERANK/OCR/VISION → ¥X/M tokens（×1000）
 */
function derivePricing(model: BackendAiModel): {
  inputPrice?: number
  outputPrice?: number
  modelPrice?: number
  unit: PriceUnit
  byVariant: boolean
} {
  const caps = model.capabilities ?? ""
  const has = (cap: string) => caps.includes(cap)

  // 视频生成：多档价格矩阵，统一展示"按规格计费"
  if (has("VIDEO_GEN") || model.quotaType === 3) {
    return { unit: "次", byVariant: true }
  }
  // 图像生成 / 3D 模型 / 其他按次计费
  if (model.quotaType === 1) {
    return {
      modelPrice: model.modelPrice ?? undefined,
      unit: "次",
      byVariant: false
    }
  }
  // 音乐生成：modelPrice 是元/秒
  if (has("MUSIC_GEN")) {
    return {
      modelPrice: model.modelPrice ?? undefined,
      unit: "秒",
      byVariant: false
    }
  }
  // 语音识别：modelPrice 是元/秒，展示为元/分钟（×60，更直观）
  if (has("SPEECH_ASR")) {
    return {
      modelPrice: model.modelPrice != null ? model.modelPrice * 60 : undefined,
      unit: "分钟",
      byVariant: false
    }
  }
  // 语音合成：inputPricePerK 是元/千字符，展示为元/万字符（×10）
  if (has("SPEECH_TTS")) {
    return {
      inputPrice: model.inputPricePerK != null ? model.inputPricePerK * 10 : undefined,
      unit: "万字符",
      byVariant: false
    }
  }
  // 默认 LLM 类（CHAT/EMBEDDING/VISION/RERANK/OCR）：元/千 token → 元/M tokens（×1000）
  return {
    inputPrice: model.inputPricePerK != null ? model.inputPricePerK * 1000 : undefined,
    outputPrice: model.outputPricePerK != null ? model.outputPricePerK * 1000 : undefined,
    unit: "M tokens",
    byVariant: false
  }
}

function adaptBackendModel(model: BackendAiModel): ModelCard {
  const kind = toModelKind(model.capabilities)
  const tags = (model.capabilities ?? "CHAT")
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .map((item) => {
      if (item === "CHAT") return "对话"
      if (item === "VISION") return "识图"
      if (item === "IMAGE_GEN") return "绘画"
      if (item === "AUDIO") return "音视频"
      if (item === "EMBEDDING") return "向量"
      return item
    })
  const isByUnit = model.quotaType === 1
  const pricing = derivePricing(model)
  return {
    id: model.modelId,
    numericId: model.id,
    name: model.displayName || model.modelName,
    modelName: model.modelName,
    provider: model.provider,
    providerLabel: providerLabel(model.provider),
    providerTone: providerTone(model.provider),
    kind,
    tags,
    description: `${model.providerType} 协议模型，实际调用名称 ${model.modelName}`,
    inputPrice: pricing.inputPrice,
    outputPrice: pricing.outputPrice,
    modelPrice: pricing.modelPrice,
    rawModelPrice: model.modelPrice != null ? Number(model.modelPrice) : undefined,
    rawInputPricePerK: model.inputPricePerK != null ? Number(model.inputPricePerK) : undefined,
    rawOutputPricePerK: model.outputPricePerK != null ? Number(model.outputPricePerK) : undefined,
    quotaType: model.quotaType ?? 0,
    priceUnit: pricing.unit,
    byVariant: pricing.byVariant,
    billing: isByUnit ? "按次计费" : "按量计费",
    enabled: model.enabled
  }
}

function formatPrice(value: number, unit: PriceUnit): string {
  // 大额（≥1）保留 2 位，小额保留 4 位，避免 ¥0.0003 显示成 ¥0.00
  const decimals = value >= 1 ? 2 : 4
  return `¥${value.toFixed(decimals)}/${unit}`
}

function providerMark(label: string): string {
  if (label === "OpenAI") return "◎"
  if (label === "Anthropic") return "✳"
  if (label === "Google") return "◆"
  return label.slice(0, 1).toUpperCase()
}

export default function ModelManagementPage() {
  const queryClient = useQueryClient()
  const uploadInputRef = useRef<HTMLInputElement | null>(null)
  const [search, setSearch] = useState("")
  const [provider, setProvider] = useState("全部供应商")
  const [kind, setKind] = useState("全部类型")
  const [tableView, setTableView] = useState(false)

  const { data } = useQuery<PageResult<BackendAiModel>>({
    queryKey: ["ai-models", "preview"],
    queryFn: () =>
      backendApi.request<PageResult<BackendAiModel>>({
        url: "/ai/models",
        params: { page: 0, size: 80 },
        showError: false
      })
  })

  const importJson = useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData()
      formData.append("file", file)
      return backendApi.request<ImportResult>({
        url: "/ai/models/import-json",
        method: "POST",
        data: formData,
        params: { providerCode: "third_party", providerName: "第三方聚合" }
      })
    },
    onSuccess: (result) => {
      notify.success(
        `导入完成，新增 ${result.createdCount} 个，更新 ${result.updatedCount} 个，选中 ${result.selectedCount} 个`
      )
      queryClient.invalidateQueries({ queryKey: ["ai-models", "preview"] })
    }
  })

  const models = useMemo(() => {
    return data?.list?.map(adaptBackendModel) ?? []
  }, [data])

  const providerCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const model of models)
      counts.set(model.providerLabel, (counts.get(model.providerLabel) ?? 0) + 1)
    return [
      { label: "全部供应商", count: models.length },
      ...Array.from(counts, ([label, count]) => ({ label, count }))
    ]
  }, [models])

  const kindCounts = useMemo(() => {
    const counts = new Map<ModelKind, number>()
    for (const model of models) counts.set(model.kind, (counts.get(model.kind) ?? 0) + 1)
    const ordered: ModelKind[] = ["文本", "图像", "音视频", "检索"]
    return [
      { label: "全部类型", count: models.length },
      ...ordered.map((label) => ({ label, count: counts.get(label) ?? 0 }))
    ]
  }, [models])

  const filteredModels = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    return models.filter((model) => {
      const matchProvider = provider === "全部供应商" || model.providerLabel === provider
      const matchKind = kind === "全部类型" || model.kind === kind
      const matchSearch =
        keyword.length === 0 ||
        model.name.toLowerCase().includes(keyword) ||
        model.description.toLowerCase().includes(keyword)
      return matchProvider && matchKind && matchSearch
    })
  }, [kind, models, provider, search])

  const resetFilters = () => {
    setSearch("")
    setProvider("全部供应商")
    setKind("全部类型")
  }

  const handleUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) importJson.mutate(file)
    event.target.value = ""
  }

  return (
    <PageContainer maxWidth={false} disablePadding>
      <main className="min-h-[calc(100vh-var(--layout-header-height))] bg-muted/30 text-foreground">
        <div className="grid min-h-[calc(100vh-var(--layout-header-height))] grid-cols-1 lg:grid-cols-[260px_1fr]">
          <aside className="border-border border-r bg-card/70 px-4 py-5 lg:sticky lg:top-[var(--layout-header-height)] lg:h-[calc(100vh-var(--layout-header-height))] lg:overflow-y-auto">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="font-semibold text-foreground text-lg">筛选</h2>
              <Button variant="outline" size="sm" onClick={resetFilters}>
                <RefreshCw className="size-4" />
                重置
              </Button>
            </div>
            <FilterGroup
              active={provider}
              title="供应商"
              items={providerCounts}
              onChange={setProvider}
            />
            <FilterGroup active={kind} title="模型类型" items={kindCounts} onChange={setKind} />
            <section className="mt-6 border-border border-t pt-5">
              <h3 className="mb-3 font-semibold text-base">标签</h3>
              <div className="flex flex-wrap gap-2">
                {["对话", "工具", "识图", "绘画", "向量", "视频"].map((tag) => (
                  <Badge key={tag} variant="outline" className="bg-card">
                    {tag}
                  </Badge>
                ))}
              </div>
            </section>
          </aside>

          <section className="space-y-4 p-4 sm:p-5">
            <div
              className="relative overflow-hidden rounded-md px-5 py-6"
              style={{
                backgroundImage: `linear-gradient(100deg,rgba(223,242,251,0.92) 0%,rgba(232,238,252,0.88) 45%,rgba(247,223,239,0.85) 100%), url('${$url.cdn("/assets/images/cover/cover-2.webp")}')`,
                backgroundSize: "cover",
                backgroundPosition: "center"
              }}
            >
              <div className="flex min-h-24 items-center justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-3">
                    <h1 className="font-semibold text-2xl text-foreground">全部供应商</h1>
                    <Badge className="bg-foreground/10 text-foreground/70 hover:bg-foreground/10">
                      Total {models.length} models
                    </Badge>
                  </div>
                  <p className="mt-3 max-w-2xl text-foreground/70">
                    查看所有可用的 AI 模型供应商，包括多家知名供应商和第三方聚合模型。
                  </p>
                </div>
                <div className="hidden h-20 w-20 place-items-center rounded-md bg-background/50 shadow-sm ring-1 ring-background/70 sm:grid">
                  <Sparkles className="size-9 text-violet-600" />
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
              <div className="relative min-w-0 flex-1">
                <Search className="absolute top-1/2 left-3 size-5 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="模糊搜索模型名称"
                  className="h-11 rounded-md border-border bg-card pl-10"
                />
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <input
                  ref={uploadInputRef}
                  type="file"
                  accept="application/json,.json"
                  className="hidden"
                  onChange={handleUpload}
                />
                {/* <Button
                  variant="outline"
                  className="bg-white"
                  disabled={importJson.isPending}
                  onClick={() => uploadInputRef.current?.click()}
                >
                  <Upload className="size-4" />
                  {importJson.isPending ? "导入中" : "导入 JSON"}
                </Button> */}
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <Button
                        variant="default"
                        onClick={() =>
                          navigator.clipboard?.writeText(filteredModels.map((m) => m.id).join("\n"))
                        }
                      >
                        <Copy className="size-4" />
                        复制
                      </Button>
                    }
                  />
                  <TooltipContent>复制当前筛选出的模型 ID</TooltipContent>
                </Tooltip>
                <Button
                  variant="outline"
                  className="bg-card"
                  onClick={() => setTableView((v) => !v)}
                >
                  {tableView ? <Grid2X2 className="size-4" /> : <Table2 className="size-4" />}
                  {tableView ? "卡片视图" : "表格视图"}
                </Button>
              </div>
            </div>

            {tableView ? (
              <ModelTable models={filteredModels} />
            ) : (
              <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(260px,1fr))]">
                {filteredModels.map((model) => (
                  <ModelCardItem key={model.id} model={model} />
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </PageContainer>
  )
}

function FilterGroup({
  title,
  active,
  items,
  onChange
}: {
  title: string
  active: string
  items: { label: string; count: number }[]
  onChange: (value: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const visibleItems = expanded ? items : items.slice(0, 4)

  return (
    <section className="mb-5">
      <h3 className="mb-3 text-center font-semibold text-base">{title}</h3>
      <div className="space-y-2">
        {visibleItems.map((item) => (
          <button
            key={item.label}
            type="button"
            onClick={() => onChange(item.label)}
            className={cn(
              "flex h-11 w-full items-center justify-between rounded-md border px-3 font-medium text-sm transition",
              active === item.label
                ? "border-transparent bg-muted text-primary shadow-sm"
                : "border-border bg-card text-foreground hover:bg-muted/50"
            )}
          >
            <span className="truncate">{item.label}</span>
            <span className="rounded-full bg-background px-2 py-0.5 text-muted-foreground text-xs ring-1 ring-border">
              {item.count}
            </span>
          </button>
        ))}
      </div>
      {items.length > 4 && (
        <button
          type="button"
          className="mt-3 flex w-full items-center justify-center gap-1 text-muted-foreground text-sm hover:text-foreground"
          onClick={() => setExpanded((value) => !value)}
        >
          <ChevronDown className={cn("size-4 transition", expanded && "rotate-180")} />
          {expanded ? "收起" : "展开更多"}
        </button>
      )}
    </section>
  )
}

function ModelCardItem({ model }: { model: ModelCard }) {
  const [editOpen, setEditOpen] = useState(false)

  function handleCopy() {
    navigator.clipboard?.writeText(model.modelName)
    notify.success(`已复制：${model.modelName}`)
  }

  return (
    <article className="flex min-h-56 flex-col rounded-md bg-card p-4 shadow-sm ring-1 ring-border transition hover:-translate-y-0.5 hover:shadow-md">
      {/* 图标 + 类型 badge 同行 */}
      <div className="mb-3 flex items-center justify-between">
        <div
          className={cn(
            "grid size-12 place-items-center rounded-xl font-semibold text-xl ring-1",
            providerTones[model.providerTone]
          )}
        >
          {providerMark(model.providerLabel)}
        </div>
        <Badge variant="outline" className="bg-card text-xs">
          {model.kind}
        </Badge>
      </div>

      {/* 模型名 + 供应商 */}
      <div className="mb-3">
        <div className="flex items-center gap-1.5">
          <h3 className="font-semibold text-base leading-tight">{model.name}</h3>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  onClick={handleCopy}
                  className="grid size-5 place-items-center rounded text-muted-foreground hover:text-foreground"
                >
                  <Copy className="size-3" />
                </button>
              }
            />
            <TooltipContent>复制模型 ID</TooltipContent>
          </Tooltip>
        </div>
        <p className="text-muted-foreground text-sm">{model.providerLabel}</p>
      </div>

      {/* 描述 */}
      <p className="mb-3 line-clamp-3 flex-1 text-muted-foreground text-sm">{model.description}</p>

      {/* 价格 */}
      <PriceBlock model={model} />

      {/* 底部：标签 + 计费 */}
      <div className="mt-3 flex items-center justify-between gap-2">
        <div className="flex flex-wrap gap-1.5">
          {model.tags.slice(0, 3).map((tag) => (
            <Badge key={tag} variant="outline" className="bg-muted font-normal text-xs">
              {tag}
            </Badge>
          ))}
          <span className="flex shrink-0 items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-emerald-700 text-xs ring-1 ring-emerald-200">
            <span className="size-1.5 rounded-full bg-emerald-500" />
            {model.billing}
          </span>
        </div>
      </div>

      {/* 编辑按钮 */}
      <button
        type="button"
        onClick={() => setEditOpen(true)}
        className="mt-3 flex w-full items-center justify-center gap-2 rounded-md border border-border py-2 text-muted-foreground text-sm transition hover:bg-muted"
      >
        <Pencil className="size-3.5" />
        编辑
      </button>

      <EditModelDialog
        key={editOpen ? "open" : "closed"}
        model={model}
        open={editOpen}
        onOpenChange={setEditOpen}
      />
    </article>
  )
}

function PriceBlock({ model }: { model: ModelCard }) {
  if (model.byVariant) {
    return <p className="text-muted-foreground text-xs">按规格计费</p>
  }
  if (model.modelPrice !== undefined) {
    return (
      <p className="text-muted-foreground text-xs">
        <span className="font-medium text-foreground">
          {formatPrice(model.modelPrice, model.priceUnit)}
        </span>
      </p>
    )
  }
  if (model.inputPrice === undefined && model.outputPrice === undefined) return null
  return (
    <div className="space-y-0.5 text-xs">
      {model.inputPrice !== undefined && (
        <p className="text-muted-foreground">
          输入{" "}
          <span className="font-medium text-foreground">
            {formatPrice(model.inputPrice, model.priceUnit)}
          </span>
        </p>
      )}
      {model.outputPrice !== undefined && (
        <p className="text-muted-foreground">
          输出{" "}
          <span className="font-medium text-foreground">
            {formatPrice(model.outputPrice, model.priceUnit)}
          </span>
        </p>
      )}
      {model.cachePrice !== undefined && (
        <p className="text-muted-foreground/70">
          缓存命中{" "}
          <span className="font-medium">{formatPrice(model.cachePrice, model.priceUnit)}</span>
        </p>
      )}
    </div>
  )
}

function ModelTable({ models }: { models: ModelCard[] }) {
  return (
    <div className="overflow-hidden rounded-md bg-card shadow-sm ring-1 ring-border">
      <div className="grid grid-cols-[minmax(220px,1.4fr)_140px_110px_120px_120px] gap-3 border-border border-b px-4 py-3 font-medium text-muted-foreground text-sm">
        <span>模型</span>
        <span>供应商</span>
        <span>类型</span>
        <span>计费</span>
        <span>状态</span>
      </div>
      {models.map((model) => (
        <div
          key={model.id}
          className="grid grid-cols-[minmax(220px,1.4fr)_140px_110px_120px_120px] gap-3 border-border/50 border-b px-4 py-3 text-sm last:border-b-0"
        >
          <div>
            <p className="font-medium">{model.name}</p>
            <p className="truncate text-muted-foreground">{model.id}</p>
          </div>
          <span>{model.providerLabel}</span>
          <span>{model.kind}</span>
          <span>{model.billing}</span>
          <span className="text-emerald-600">{model.enabled ? "启用" : "停用"}</span>
        </div>
      ))}
    </div>
  )
}

interface EditModelForm {
  displayName: string
  baseUrl: string
  apiKey: string
  quotaType: string
  inputPricePerK: string
  outputPricePerK: string
  modelPrice: string
  enabled: boolean
  remark: string
}

const QUOTA_TYPE_OPTIONS = [
  { value: "0", label: "按量（token）" },
  { value: "1", label: "按次" },
  { value: "2", label: "按秒" },
  { value: "3", label: "按单元（视频等）" }
]

function EditModelDialog({
  model,
  open,
  onOpenChange
}: {
  model: ModelCard
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<EditModelForm>({
    displayName: model.name,
    baseUrl: "",
    apiKey: "",
    quotaType: String(model.quotaType ?? 0),
    inputPricePerK: model.rawInputPricePerK != null ? String(model.rawInputPricePerK) : "",
    outputPricePerK: model.rawOutputPricePerK != null ? String(model.rawOutputPricePerK) : "",
    modelPrice: model.rawModelPrice != null ? String(model.rawModelPrice) : "",
    enabled: model.enabled,
    remark: ""
  })

  const save = useMutation({
    mutationFn: () => {
      const qt = Number(form.quotaType)
      return backendApi.request<BackendAiModel>({
        url: `/ai/models/${model.numericId}`,
        method: "PUT",
        data: {
          displayName: form.displayName || null,
          baseUrl: form.baseUrl || null,
          apiKey: form.apiKey || null,
          quotaType: qt,
          inputPricePerK: qt === 0 && form.inputPricePerK ? Number(form.inputPricePerK) : null,
          outputPricePerK: qt === 0 && form.outputPricePerK ? Number(form.outputPricePerK) : null,
          modelPrice: qt !== 0 && form.modelPrice ? Number(form.modelPrice) : null,
          enabled: form.enabled,
          remark: form.remark || null
        }
      })
    },
    onSuccess: () => {
      notify.success("保存成功")
      queryClient.invalidateQueries({ queryKey: ["ai-models", "preview"] })
      onOpenChange(false)
    }
  })

  function set<K extends keyof EditModelForm>(key: K, value: EditModelForm[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const qt = Number(form.quotaType)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>编辑模型 · {model.name}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-1">
          <div className="grid gap-1.5">
            <Label htmlFor="em-displayName">显示名称</Label>
            <Input
              id="em-displayName"
              value={form.displayName}
              onChange={(e) => set("displayName", e.target.value)}
              placeholder="显示名称"
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="em-baseUrl">API 地址</Label>
            <Input
              id="em-baseUrl"
              value={form.baseUrl}
              onChange={(e) => set("baseUrl", e.target.value)}
              placeholder="留空不修改"
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="em-apiKey">API Key</Label>
            <Input
              id="em-apiKey"
              type="password"
              value={form.apiKey}
              onChange={(e) => set("apiKey", e.target.value)}
              placeholder="留空不修改"
              autoComplete="new-password"
            />
          </div>

          {/* 计费类型 */}
          <div className="grid gap-1.5">
            <Label>计费类型</Label>
            <div className="flex flex-wrap gap-2">
              {QUOTA_TYPE_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => set("quotaType", opt.value)}
                  className={cn(
                    "rounded-md border px-3 py-1.5 text-sm transition",
                    form.quotaType === opt.value
                      ? "border-transparent bg-primary text-primary-foreground"
                      : "border-border bg-muted/50 text-foreground hover:bg-muted"
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 按量（token）：输入 + 输出价格 */}
          {qt === 0 && (
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="em-inputPrice">输入价格（元/千 token）</Label>
                <Input
                  id="em-inputPrice"
                  type="number"
                  value={form.inputPricePerK}
                  onChange={(e) => set("inputPricePerK", e.target.value)}
                  placeholder="0.000"
                />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="em-outputPrice">输出价格（元/千 token）</Label>
                <Input
                  id="em-outputPrice"
                  type="number"
                  value={form.outputPricePerK}
                  onChange={(e) => set("outputPricePerK", e.target.value)}
                  placeholder="0.000"
                />
              </div>
            </div>
          )}

          {/* 按次 / 按秒 / 按单元：固定单价 */}
          {qt !== 0 && (
            <div className="grid gap-1.5">
              <Label htmlFor="em-modelPrice">
                {qt === 1 ? "单价（元/次）" : qt === 2 ? "单价（元/秒）" : "单价（元/单元）"}
              </Label>
              <Input
                id="em-modelPrice"
                type="number"
                value={form.modelPrice}
                onChange={(e) => set("modelPrice", e.target.value)}
                placeholder="0.000"
              />
            </div>
          )}

          <div className="grid gap-1.5">
            <Label htmlFor="em-remark">备注</Label>
            <Input
              id="em-remark"
              value={form.remark}
              onChange={(e) => set("remark", e.target.value)}
              placeholder="备注信息"
            />
          </div>
          <div className="flex items-center gap-3">
            <Switch
              id="em-enabled"
              checked={form.enabled}
              onCheckedChange={(v) => set("enabled", v)}
            />
            <Label htmlFor="em-enabled">启用</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
