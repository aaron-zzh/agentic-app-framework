/**
 * CounterWidget——数字统计卡片（Minimal 风格）。
 *
 * <p>视觉特性：渐变背景（按 config.color）+ 圆角图标徽章（按 config.icon）+
 * 大号统计数字 + 右上趋势百分比 + 右下迷你 sparkline + halftone 点阵装饰。
 *
 * <p>数据契约：trend 和 sparkline 为可选字段，后端不返回时不渲染对应区块，
 * 不影响主数字显示。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import {
  Activity,
  BadgeCheck,
  Banknote,
  BookOpen,
  Bot,
  Box,
  CheckSquare,
  CreditCard,
  Database,
  FileText,
  Heart,
  Image,
  Layers,
  type LucideIcon,
  Mail,
  MessageSquare,
  Package,
  Receipt,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Ticket,
  TrendingDown,
  TrendingUp,
  Trophy,
  Users,
  Video,
  Wallet,
  Wand2
} from "lucide-react"
import { Skeleton } from "@/components/ui/skeleton"
import type { CounterWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"
import { cn } from "@/lib/utils/cn"
import { Sparkline } from "./_shared/Sparkline"
import { formatCompactNumber, resolveCardStyle } from "./_shared/styles"

interface CounterWidgetProps {
  widgetId: string
  title: string
  config: CounterWidgetConfig
  refreshInterval?: number
}

/** 图标名 → lucide 组件映射（counter 卡片常用集，未匹配时回落 TrendingUp）。 */
const ICON_MAP: Record<string, LucideIcon> = {
  activity: Activity,
  "badge-check": BadgeCheck,
  banknote: Banknote,
  "book-open": BookOpen,
  bot: Bot,
  box: Box,
  "check-square": CheckSquare,
  "credit-card": CreditCard,
  database: Database,
  "file-text": FileText,
  heart: Heart,
  image: Image,
  layers: Layers,
  mail: Mail,
  "message-square": MessageSquare,
  package: Package,
  receipt: Receipt,
  "shopping-bag": ShoppingBag,
  "shopping-cart": ShoppingCart,
  sparkles: Sparkles,
  ticket: Ticket,
  trophy: Trophy,
  users: Users,
  video: Video,
  wallet: Wallet,
  "wand-2": Wand2
}

export function CounterWidget({ widgetId, title, config, refreshInterval }: CounterWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)
  const styles = resolveCardStyle(config.color)
  const Icon = (config.icon && ICON_MAP[config.icon]) || TrendingUp
  const trend = data?.trend
  const sparkline = data?.sparkline
  const isPositive = (trend ?? 0) >= 0

  if (isLoading) {
    return (
      <div className={cn("relative h-full overflow-hidden rounded-xl p-5", styles.gradient)}>
        <div className="flex items-start justify-between">
          <Skeleton className="h-12 w-12 rounded-2xl" />
          <Skeleton className="h-4 w-12" />
        </div>
        <Skeleton className="mt-4 h-4 w-24" />
        <Skeleton className="mt-2 h-8 w-20" />
      </div>
    )
  }

  return (
    <div
      className={cn(
        "relative h-full overflow-hidden rounded-xl shadow-sm",
        "transition-shadow hover:shadow-md",
        styles.gradient
      )}
    >
      {/* 装饰层：shape-square 点阵 SVG，占满卡片，低 opacity 不抢主体 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-left-bottom bg-no-repeat opacity-25"
        style={{
          backgroundImage: "url(/assets/images/svg/shape-square.svg)",
          backgroundSize: "70%"
        }}
      />

      <div className="relative flex h-full flex-col p-4">
        {/* 顶部：图标 + 趋势 */}
        <div className="flex items-start justify-between gap-2">
          <Icon
            className={cn("h-8 w-8 shrink-0", styles.iconFg)}
            strokeWidth={1.75}
            aria-hidden="true"
          />
          {trend !== undefined && (
            <div className={cn("flex items-center gap-0.5 font-medium text-xs", styles.trendColor)}>
              {isPositive ? (
                <TrendingUp className="h-3.5 w-3.5" />
              ) : (
                <TrendingDown className="h-3.5 w-3.5" />
              )}
              <span>
                {isPositive ? "+" : ""}
                {trend.toFixed(1)}%
              </span>
            </div>
          )}
        </div>

        {/* 中部：标题（mt-auto 让标题与下方数字一起贴底，顶部留白吸收高度差） */}
        <p className={cn("mt-auto truncate pt-3 text-sm opacity-80", styles.fg)} title={title}>
          {title}
        </p>

        {/* 底部：大数字 + sparkline */}
        <div className="mt-0.5 flex items-end justify-between gap-2">
          <p className={cn("font-bold text-2xl tracking-tight", styles.fg)}>
            {formatCompactNumber(data?.value)}
          </p>
          {sparkline && sparkline.length >= 2 && (
            <Sparkline data={sparkline} color={styles.sparklineColor} />
          )}
        </div>
      </div>
    </div>
  )
}
