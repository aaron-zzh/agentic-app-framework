/**
 * ExpensesCategoryWidget——分类消耗饼图 + 分类列表。
 *
 * <p>同时支持两种数据形态：
 * <ul>
 *   <li>{@code series} — 旧 mock/通用形态，前端传 {label/value/icon}</li>
 *   <li>{@code categories} — 后端 billing-category 返回的 {biz_type/total} 行</li>
 * </ul>
 *
 * <p>biz_type → 中文名 + 图标 的映射在本组件内置（默认覆盖 AAF 业务）。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import {
  Bot,
  type LucideIcon,
  MessageSquare,
  Package,
  Sparkles,
  Video,
  Wand2,
  Wrench
} from "lucide-react"
import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"

export interface CategoryItem {
  label: string
  value: number
  icon: string
}

/** 后端 billing-category 行：{biz_type, total} */
export interface BillingCategoryRow {
  biz_type: string
  total: number
}

interface ExpensesCategoryWidgetProps {
  title?: string
  series?: CategoryItem[]
  categories?: BillingCategoryRow[]
}

const COLORS = [
  "#6366f1",
  "#ef4444",
  "#3b82f6",
  "#f97316",
  "#0ea5e9",
  "#22c55e",
  "#8b5cf6",
  "#eab308"
]

/** 业务类型 → 显示标签 + lucide 图标（同时支持 bizType 和 category 两套 key）。 */
const BIZ_TYPE_META: Record<string, { label: string; icon: LucideIcon }> = {
  // category 维度（后端 credit_transaction.category，优先匹配）
  chat: { label: "AI 对话", icon: MessageSquare },
  image_gen: { label: "图像生成", icon: Sparkles },
  image_edit: { label: "图像编辑", icon: Wand2 },
  video: { label: "视频生成", icon: Video },
  speech_tts: { label: "配音合成", icon: Bot },
  speech_asr: { label: "语音识别", icon: Bot },
  model_3d: { label: "3D 生成", icon: Package },
  avatar: { label: "数字人", icon: Bot },
  tool: { label: "工具调用", icon: Wrench },
  embedding: { label: "知识库", icon: Package },
  entitlement: { label: "权益补充", icon: Package },
  other: { label: "其他", icon: Package },
  // bizType 维度（兼容旧数据）
  AIGC_TASK: { label: "AI 创作", icon: Sparkles },
  AIGC_IMAGE: { label: "图像生成", icon: Sparkles },
  AIGC_VIDEO: { label: "视频生成", icon: Video },
  TOOL_CALL_AUDIT: { label: "工具调用", icon: Wrench },
  TOOL_CALL: { label: "工具调用", icon: Wrench },
  CHAT: { label: "AI 对话", icon: MessageSquare },
  AGENT: { label: "智能体", icon: Bot },
  COPYWRITING: { label: "文案生成", icon: Wand2 },
  PACKAGE: { label: "套餐", icon: Package },
  OTHER: { label: "其他", icon: Package }
}

function bizMeta(bizType: string) {
  return BIZ_TYPE_META[bizType] ?? { label: bizType || "其他", icon: Package }
}

export function ExpensesCategoryWidget({ title, series, categories }: ExpensesCategoryWidgetProps) {
  // 优先用真实数据 categories（biz_type 形态），否则用 series（mock 形态）
  const items: CategoryItem[] = useMemo(() => {
    if (categories && categories.length > 0) {
      return categories.map((c) => {
        const meta = bizMeta(c.biz_type)
        return { label: meta.label, value: Number(c.total), icon: c.biz_type }
      })
    }
    return series ?? []
  }, [categories, series])

  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "item", formatter: "{b}: {c} 积分 ({d}%)" },
      series: [
        {
          type: "pie",
          radius: ["30%", "70%"],
          center: ["50%", "50%"],
          roseType: "area",
          itemStyle: { borderRadius: 4 },
          label: { show: false },
          data: items.map((item, i) => ({
            name: item.label,
            value: item.value,
            itemStyle: { color: COLORS[i % COLORS.length] }
          }))
        }
      ]
    }),
    [items]
  )

  const total = items.reduce((s, i) => s + i.value, 0)
  const isBilling = !!categories

  return (
    <Card className={cn(WIDGET_CARD_CLASS)}>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">
          {title ?? (isBilling ? "积分消耗分类" : "分类")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {items.length === 0 ? (
          <div className="flex h-44 items-center justify-center text-muted-foreground text-sm">
            暂无消耗数据
          </div>
        ) : (
          <>
            <div className="flex flex-col items-center gap-6 md:flex-row">
              <BaseChart option={option} className="h-44 w-44 shrink-0" />
              <div className="grid flex-1 grid-cols-2 gap-2">
                {items.map((item, i) => {
                  const Icon = isBilling ? bizMeta(item.icon).icon : Package
                  return (
                    <div key={item.label} className="flex items-center gap-2">
                      <span
                        className="flex h-7 w-7 items-center justify-center rounded-full"
                        style={{
                          backgroundColor: `${COLORS[i % COLORS.length]}20`,
                          color: COLORS[i % COLORS.length]
                        }}
                      >
                        <Icon className="h-4 w-4" />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate font-medium text-sm">{item.label}</p>
                        <p className="text-muted-foreground text-xs">
                          {isBilling ? `${item.value} 积分` : `$${item.value}`}
                        </p>
                      </div>
                      <span
                        className="ml-auto h-2 w-2 shrink-0 rounded-full"
                        style={{ backgroundColor: COLORS[i % COLORS.length] }}
                      />
                    </div>
                  )
                })}
              </div>
            </div>
            <Separator className="my-4 border-dashed" />
            <div className="grid grid-cols-2 text-center">
              <div className="py-2">
                <p className="text-muted-foreground text-xs">
                  {isBilling ? "分类数" : "Categories"}
                </p>
                <p className="font-bold text-xl">{items.length}</p>
              </div>
              <div className="border-l py-2">
                <p className="text-muted-foreground text-xs">
                  {isBilling ? "30 天总消耗" : "Total spent"}
                </p>
                <p className="font-bold text-xl">
                  {isBilling ? `${total.toLocaleString()} 积分` : `$${total.toLocaleString()}`}
                </p>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
