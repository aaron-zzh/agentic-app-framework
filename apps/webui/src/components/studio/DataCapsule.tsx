/**
 * DataCapsule——数据胶囊
 *
 * 首屏顶部 4 张数据胶囊，用于展示"今日生成 / 作品总数 / 积分余额 / 待办"等核心指标。
 * 含趋势小箭头、轻量动效、骨架态。
 *
 * @example
 *   <DataCapsule label="今日生成" value={42} delta={12} icon={<Sparkles />} />
 *   <DataCapsule label="积分余额" value="1.2k" tone="violet" />
 */

import { TrendingDown, TrendingUp } from "lucide-react"
import type * as React from "react"
import { cn } from "@/lib/utils/index"
import { GlassCard } from "./GlassCard"

type DataCapsuleTone = "default" | "violet" | "cyan" | "emerald" | "amber"

interface DataCapsuleProps {
  /** 指标名称 */
  label: string
  /** 指标值（已格式化） */
  value: React.ReactNode
  /** 单位（如 "积分" "次"） */
  unit?: string
  /** 增量（正负数，单位 % 或绝对数）；undefined 时不显示趋势 */
  delta?: number
  /** 增量后缀（默认 %） */
  deltaSuffix?: string
  /** 前置图标 */
  icon?: React.ReactNode
  /** 色调 */
  tone?: DataCapsuleTone
  /** 加载中骨架 */
  loading?: boolean
  /** 点击事件 */
  onClick?: () => void
  /** 底部右侧操作区（如充值按钮） */
  action?: React.ReactNode
  className?: string
}

const TONE_ICON_MAP: Record<DataCapsuleTone, string> = {
  default: "text-foreground/60",
  violet: "text-violet-300",
  cyan: "text-cyan-300",
  emerald: "text-emerald-300",
  amber: "text-amber-300"
}

export function DataCapsule({
  label,
  value,
  unit,
  delta,
  deltaSuffix = "%",
  icon,
  tone = "default",
  loading,
  onClick,
  action,
  className
}: DataCapsuleProps) {
  const interactive = Boolean(onClick)
  const deltaPositive = typeof delta === "number" && delta >= 0

  return (
    <GlassCard
      glow={
        tone === "default"
          ? "accent"
          : (tone as Exclude<DataCapsuleTone, "default" | "amber" | "emerald">)
      }
      interactive={interactive}
      onClick={onClick}
      className={cn("min-h-[112px]", className)}
    >
      <div className="flex h-full flex-col justify-between p-4">
        {/* 头：图标 + label */}
        <div className="flex items-center gap-2">
          {icon && (
            <span
              className={cn(
                "flex size-7 items-center justify-center rounded-lg bg-foreground/[0.04]",
                TONE_ICON_MAP[tone]
              )}
            >
              {icon}
            </span>
          )}
          <span className="text-muted-foreground text-xs">{label}</span>
        </div>

        {/* 主：值 + 单位 + 趋势/操作 */}
        <div className="flex items-end justify-between gap-2">
          {loading ? (
            <span className="h-8 w-20 animate-pulse rounded bg-foreground/10" />
          ) : (
            <div className="flex items-baseline gap-1">
              <span className="font-semibold text-2xl tabular-nums leading-none">{value}</span>
              {unit && <span className="text-muted-foreground text-xs">{unit}</span>}
            </div>
          )}
          {action}
          {typeof delta === "number" && (
            <span
              className={cn(
                "inline-flex items-center gap-0.5 font-medium text-xs tabular-nums",
                deltaPositive ? "text-emerald-400" : "text-rose-400"
              )}
            >
              {deltaPositive ? (
                <TrendingUp className="size-3" />
              ) : (
                <TrendingDown className="size-3" />
              )}
              {Math.abs(delta)}
              {deltaSuffix}
            </span>
          )}
        </div>
      </div>
    </GlassCard>
  )
}
