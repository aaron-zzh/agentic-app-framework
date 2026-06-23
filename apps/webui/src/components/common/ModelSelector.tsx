/**
 * ModelSelector——通用模型选择展示组件（纯展示层，不管理状态）
 *
 * 配合 useModelSelector 或 useGenerationParams 使用：
 *   const { options, modelId, setModelId } = useModelSelector("CHAT")
 *   <ModelSelector options={options} value={modelId} onChange={setModelId} />
 *
 * variant="dropdown"（默认）：图标 + 模型名 + 箭头，适合 Composer 工具栏
 * variant="select"：Select 控件，适合参数栏
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDownIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import type { AiModelVO } from "@/lib/api/rest/ai/ai-model"
import type { ModelOption } from "@/lib/hooks/use-model-selector"

interface ModelSelectorProps {
  options: ModelOption[]
  value?: string
  onChange?: (modelId: string, model: AiModelVO) => void
  variant?: "dropdown" | "select"
  placeholder?: string
  className?: string
}

// 按 modelName 关键词匹配品牌图标（优先级从上到下，first match wins）
const BRAND_ICON_RULES: [keyword: string, icon: string][] = [
  ["happyhorse", "/assets/brand/happyhorse.png"],
  ["qwen", "/assets/brand/qwen.png"],
  ["claude", "/assets/brand/claude.svg"],
  ["gemini", "/assets/brand/gemini.svg"],
  ["gpt", "/assets/brand/chatgpt.svg"],
  ["o1-", "/assets/brand/chatgpt.svg"],
  ["o3-", "/assets/brand/chatgpt.svg"],
  ["o4-", "/assets/brand/chatgpt.svg"]
]

function getBrandIcon(option: ModelOption): string | null {
  // 用 modelName 匹配（最精准），fallback 到 modelId
  const name = (option.meta.modelName ?? option.value).toLowerCase()
  return BRAND_ICON_RULES.find(([kw]) => name.includes(kw))?.[1] ?? null
}

const PROVIDER_COLORS: Record<string, string> = {
  openai: "bg-black text-white",
  anthropic: "bg-orange-500 text-white",
  google: "bg-blue-500 text-white",
  aliyun: "bg-violet-500 text-white",
  qwen: "bg-violet-500 text-white",
  volcengine: "bg-blue-600 text-white",
  deepseek: "bg-sky-500 text-white",
  xai: "bg-green-600 text-white",
  happyhorse: "bg-cyan-500 text-white"
}

function ModelAvatar({ option }: { option: ModelOption }) {
  const provider = option.meta.provider ?? ""
  const icon = getBrandIcon(option)
  if (icon) {
    return (
      // biome-ignore lint/performance/noImgElement: brand icon, no Next.js Image needed for small avatars
      <img src={icon} alt={provider} className="size-5 shrink-0 rounded-full object-contain" />
    )
  }
  const colorClass = PROVIDER_COLORS[provider] ?? "bg-slate-400 text-white"
  return (
    <span
      className={`inline-flex size-5 shrink-0 items-center justify-center rounded-full font-semibold text-[10px] ${colorClass}`}
    >
      {(provider.slice(0, 1) || "?").toUpperCase()}
    </span>
  )
}

export function ModelSelector({
  options,
  value,
  onChange,
  variant = "dropdown",
  placeholder = "选择模型",
  className
}: ModelSelectorProps) {
  const currentModel = options.find((o) => o.value === value)

  function handleSelect(id: string | null) {
    if (!id) return
    const model = options.find((o) => o.value === id)
    if (model) onChange?.(id, model.meta)
  }

  if (options.length === 0) return null

  if (variant === "select") {
    return (
      <Select value={value ?? ""} onValueChange={handleSelect}>
        <SelectTrigger className={className ?? "h-8 w-[180px] text-xs"}>
          <div className="flex min-w-0 items-center gap-1.5">
            {currentModel && <ModelAvatar option={currentModel} />}
            <span className="truncate">{currentModel?.label ?? value}</span>
          </div>
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              <div className="flex items-center gap-2">
                <ModelAvatar option={o} />
                <span>{o.label}</span>
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            className={
              className ??
              "h-7 gap-1 rounded-full px-2 text-muted-foreground text-xs hover:text-foreground"
            }
          />
        }
      >
        {currentModel && <ModelAvatar option={currentModel} />}
        <span className="max-w-40 truncate">{currentModel?.label ?? placeholder}</span>
        <ChevronDownIcon className="size-3" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="max-h-60 min-w-48 overflow-y-auto">
        {options.map((o) => (
          <DropdownMenuItem
            key={o.value}
            onClick={() => handleSelect(o.value)}
            className={o.value === value ? "bg-accent" : ""}
          >
            <ModelAvatar option={o} />
            <span>{o.label}</span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
