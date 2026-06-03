"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  Check,
  ChevronDown,
  Copy,
  Grid2X2,
  RefreshCw,
  Search,
  Sparkles,
  Table2,
  Upload
} from "lucide-react"
import { useMemo, useRef, useState } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"
import { cn } from "@/lib/utils/cn"

type ModelKind = "文本" | "图像" | "音视频" | "检索"
type BillingKind = "按量计费" | "按次计费"

interface ModelCard {
  id: string
  name: string
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

const sampleModels: ModelCard[] = [
  {
    id: "anthropic:claude-opus-4-8",
    name: "claude-opus-4-8",
    provider: "anthropic",
    providerLabel: "Anthropic",
    providerTone: "orange",
    kind: "文本",
    tags: ["对话", "识图", "工具"],
    description: "Anthropic 高强度推理模型，适合长周期任务、代码协作和复杂 Agent 场景。",
    inputPrice: 5,
    outputPrice: 25,
    cachePrice: 0.5,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "google:gemini-3.5-flash",
    name: "gemini-3.5-flash",
    provider: "google",
    providerLabel: "Google",
    providerTone: "blue",
    kind: "文本",
    tags: ["对话", "工具", "识图"],
    description: "高吞吐 Flash 模型，适合智能体执行、翻译、信息处理和低延迟任务。",
    inputPrice: 3.6,
    outputPrice: 21.6,
    cachePrice: 0.36,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "openai:gpt-image-2",
    name: "gpt-image-2",
    provider: "openai",
    providerLabel: "OpenAI",
    providerTone: "slate",
    kind: "图像",
    tags: ["绘画", "dall-e-3格式"],
    description: "高质量图像生成与编辑模型，支持灵活尺寸和高保真输入。",
    inputPrice: 3,
    outputPrice: 18,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "qwen:qwen-image-2",
    name: "qwen-image-2",
    provider: "aliyun",
    providerLabel: "阿里百炼",
    providerTone: "violet",
    kind: "图像",
    tags: ["绘画"],
    description: "Qwen-Image 系列图像模型，兼顾中文文字渲染、真实质感和复杂指令遵循。",
    modelPrice: 0.26,
    billing: "按次计费",
    enabled: true
  },
  {
    id: "qwen:qwen3.7-max",
    name: "qwen3.7-max",
    provider: "aliyun",
    providerLabel: "阿里百炼",
    providerTone: "violet",
    kind: "文本",
    tags: ["对话", "工具"],
    description: "Qwen 系列综合能力旗舰模型，面向 Agent、代码、办公和生产力任务。",
    inputPrice: 12,
    outputPrice: 36,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "wan:wan2.7-image",
    name: "wan2.7-image",
    provider: "aliyun",
    providerLabel: "阿里百炼",
    providerTone: "violet",
    kind: "图像",
    tags: ["绘画"],
    description: "万相图像生成与编辑旗舰模型，支持文生图、图像编辑和多图参考生成。",
    modelPrice: 0.65,
    billing: "按次计费",
    enabled: true
  },
  {
    id: "google:gemini-3.1-pro",
    name: "gemini-3.1-pro",
    provider: "google",
    providerLabel: "Google",
    providerTone: "blue",
    kind: "文本",
    tags: ["对话", "工具", "识图"],
    description: "面向跨模态推理和复杂任务规划的 Gemini Pro 模型。",
    inputPrice: 0.375,
    outputPrice: 2.25,
    cachePrice: 0.0375,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "openai:gpt-chat-latest",
    name: "gpt-chat-latest",
    provider: "openai",
    providerLabel: "OpenAI",
    providerTone: "slate",
    kind: "文本",
    tags: ["对话", "工具"],
    description: "指向 ChatGPT 当前即时模型，适合日常对话和轻量任务。",
    inputPrice: 7.5,
    outputPrice: 45,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "xai:grok-4.2-fast",
    name: "grok-4.2-fast",
    provider: "xai",
    providerLabel: "xAI",
    providerTone: "green",
    kind: "文本",
    tags: ["文本"],
    description: "Grok 快速经济版模型，面向通用对话、信息分析和高频请求。",
    inputPrice: 0.4,
    outputPrice: 3,
    billing: "按量计费",
    enabled: true
  },
  {
    id: "pixverse:template",
    name: "pixverse-image-template",
    provider: "pixverse",
    providerLabel: "Pixverse",
    providerTone: "rose",
    kind: "音视频",
    tags: ["图片模板"],
    description: "以图片为核心的短视频生成模板模型，适合素材驱动的视频创作。",
    modelPrice: 0.041,
    billing: "按次计费",
    enabled: true
  },
  {
    id: "pixverse:lipsync",
    name: "pixverse-lipsync",
    provider: "pixverse",
    providerLabel: "Pixverse",
    providerTone: "rose",
    kind: "音视频",
    tags: ["视频", "对口型"],
    description: "对口型视频模型，用于解决人物视频中的口型同步任务。",
    modelPrice: 0.041,
    billing: "按次计费",
    enabled: true
  },
  {
    id: "deepseek:deepseek-chat",
    name: "deepseek-chat",
    provider: "deepseek",
    providerLabel: "DeepSeek",
    providerTone: "blue",
    kind: "文本",
    tags: ["对话", "工具"],
    description: "高性价比通用对话模型，适合代码、知识问答和结构化输出。",
    inputPrice: 0.2,
    outputPrice: 0.8,
    billing: "按量计费",
    enabled: true
  }
]

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
  return {
    id: model.modelId,
    name: model.displayName || model.modelName,
    provider: model.provider,
    providerLabel: providerLabel(model.provider),
    providerTone: providerTone(model.provider),
    kind,
    tags,
    description: `${model.providerType} 协议模型，实际调用名称 ${model.modelName}`,
    inputPrice: model.inputPricePerK ? model.inputPricePerK * 1000 : undefined,
    outputPrice: model.outputPricePerK ? model.outputPricePerK * 1000 : undefined,
    billing: "按量计费",
    enabled: model.enabled
  }
}

function uniqueModels(models: ModelCard[]): ModelCard[] {
  const seen = new Set<string>()
  return models.filter((model) => {
    if (seen.has(model.id)) return false
    seen.add(model.id)
    return true
  })
}

function formatPrice(value: number): string {
  return `$${value.toFixed(value >= 1 ? 4 : 3)}/M`
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
    const backendModels = data?.list?.map(adaptBackendModel) ?? []
    return uniqueModels([...backendModels, ...sampleModels])
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
      <main className="min-h-[calc(100vh-var(--layout-header-height))] bg-[#f6f8fb] text-slate-950">
        <div className="grid min-h-[calc(100vh-var(--layout-header-height))] grid-cols-1 lg:grid-cols-[260px_1fr]">
          <aside className="border-slate-200 border-r bg-white/70 px-4 py-5 lg:sticky lg:top-[var(--layout-header-height)] lg:h-[calc(100vh-var(--layout-header-height))] lg:overflow-y-auto">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="font-semibold text-lg">筛选</h2>
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
            <section className="mt-6 border-slate-200 border-t pt-5">
              <h3 className="mb-3 font-semibold text-base">标签</h3>
              <div className="flex flex-wrap gap-2">
                {["对话", "工具", "识图", "绘画", "向量", "视频"].map((tag) => (
                  <Badge key={tag} variant="outline" className="bg-white">
                    {tag}
                  </Badge>
                ))}
              </div>
            </section>
          </aside>

          <section className="space-y-4 p-4 sm:p-5">
            <div className="relative overflow-hidden rounded-md bg-[linear-gradient(100deg,#dff2fb_0%,#e8eefc_45%,#f7dfef_100%)] px-5 py-6">
              <div className="flex min-h-24 items-center justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-3">
                    <h1 className="font-semibold text-2xl">全部供应商</h1>
                    <Badge className="bg-slate-900/10 text-slate-700 hover:bg-slate-900/10">
                      Total {models.length} models
                    </Badge>
                  </div>
                  <p className="mt-3 max-w-2xl text-slate-700">
                    查看所有可用的 AI 模型供应商，包括多家知名供应商和第三方聚合模型。
                  </p>
                </div>
                <div className="hidden h-20 w-20 place-items-center rounded-md bg-white/50 shadow-sm ring-1 ring-white/70 sm:grid">
                  <Sparkles className="size-9 text-violet-600" />
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
              <div className="relative min-w-0 flex-1">
                <Search className="absolute top-1/2 left-3 size-5 -translate-y-1/2 text-slate-500" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="模糊搜索模型名称"
                  className="h-11 rounded-md border-slate-200 bg-white pl-10"
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
                <Button
                  variant="outline"
                  className="bg-white"
                  disabled={importJson.isPending}
                  onClick={() => uploadInputRef.current?.click()}
                >
                  <Upload className="size-4" />
                  {importJson.isPending ? "导入中" : "导入 JSON"}
                </Button>
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
                <div className="flex h-11 items-center gap-2 rounded-md bg-white px-3 text-slate-600 text-sm ring-1 ring-slate-200">
                  倍率
                  <Switch checked={false} />
                </div>
                <Button
                  variant="outline"
                  className="bg-white"
                  onClick={() => setTableView((v) => !v)}
                >
                  {tableView ? <Grid2X2 className="size-4" /> : <Table2 className="size-4" />}
                  {tableView ? "卡片视图" : "表格视图"}
                </Button>
                <Button variant="outline" className="bg-white px-3">
                  M
                </Button>
              </div>
            </div>

            {tableView ? (
              <ModelTable models={filteredModels} />
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-4">
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
                ? "border-transparent bg-slate-100 text-blue-700 shadow-sm"
                : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
            )}
          >
            <span className="truncate">{item.label}</span>
            <span className="rounded-full bg-white px-2 py-0.5 text-slate-700 text-xs ring-1 ring-slate-200">
              {item.count}
            </span>
          </button>
        ))}
      </div>
      {items.length > 4 && (
        <button
          type="button"
          className="mt-3 flex w-full items-center justify-center gap-1 text-slate-500 text-sm hover:text-slate-800"
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
  return (
    <article className="min-h-56 rounded-md bg-white p-4 shadow-sm ring-1 ring-slate-200 transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="mb-3 flex items-start gap-3">
        <div
          className={cn(
            "grid size-10 shrink-0 place-items-center rounded-md font-semibold ring-1",
            providerTones[model.providerTone]
          )}
        >
          {providerMark(model.providerLabel)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-start justify-between gap-2">
            <h3 className="truncate font-semibold text-lg">{model.name}</h3>
            <Badge variant="outline" className="shrink-0 bg-white">
              {model.kind}
            </Badge>
          </div>
          <PriceBlock model={model} />
        </div>
        <div className="flex shrink-0 gap-2">
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="grid size-8 place-items-center rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50"
                >
                  <Copy className="size-4" />
                </button>
              }
            />
            <TooltipContent>复制模型 ID</TooltipContent>
          </Tooltip>
          <button
            type="button"
            className="grid size-8 place-items-center rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50"
          >
            <Check className="size-4" />
          </button>
        </div>
      </div>
      <p className="line-clamp-2 min-h-10 text-slate-600 text-sm">{model.description}</p>
      <div className="mt-5 flex items-end justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {model.tags.slice(0, 3).map((tag) => (
            <Badge key={tag} variant="outline" className="bg-white font-normal">
              {tag}
            </Badge>
          ))}
        </div>
        <span className="flex shrink-0 items-center gap-1 rounded-full bg-slate-50 px-2 py-1 text-slate-600 text-xs">
          <span className="size-2 rounded-full bg-emerald-500" />
          {model.billing}
        </span>
      </div>
    </article>
  )
}

function PriceBlock({ model }: { model: ModelCard }) {
  if (model.modelPrice !== undefined) {
    return <p className="mt-1 text-slate-600 text-sm">模型价格 {formatPrice(model.modelPrice)}</p>
  }
  return (
    <div className="mt-1 space-y-0.5 text-sm">
      {model.inputPrice !== undefined && <p>输入价格 {formatPrice(model.inputPrice)}</p>}
      {model.outputPrice !== undefined && <p>补全价格 {formatPrice(model.outputPrice)}</p>}
      {model.cachePrice !== undefined && (
        <p className="text-slate-500">缓存命中价格 {formatPrice(model.cachePrice)}</p>
      )}
    </div>
  )
}

function ModelTable({ models }: { models: ModelCard[] }) {
  return (
    <div className="overflow-hidden rounded-md bg-white shadow-sm ring-1 ring-slate-200">
      <div className="grid grid-cols-[minmax(220px,1.4fr)_140px_110px_120px_120px] gap-3 border-slate-200 border-b px-4 py-3 font-medium text-slate-600 text-sm">
        <span>模型</span>
        <span>供应商</span>
        <span>类型</span>
        <span>计费</span>
        <span>状态</span>
      </div>
      {models.map((model) => (
        <div
          key={model.id}
          className="grid grid-cols-[minmax(220px,1.4fr)_140px_110px_120px_120px] gap-3 border-slate-100 border-b px-4 py-3 text-sm last:border-b-0"
        >
          <div>
            <p className="font-medium">{model.name}</p>
            <p className="truncate text-slate-500">{model.id}</p>
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
