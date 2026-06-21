/**
 * PlanCompareTable——所有套餐 × 所有权益的对比矩阵
 *
 * 数据来源：plans 列表中所有 plan 的 entitlements 取并集，按 code 去重；
 * 行：每条权益；列：每个套餐。
 *
 * 视觉：
 *  - 头部 sticky，水平滚动时套餐名常驻
 *  - 高亮"推荐"列（与 PRO 套餐边框色一致）
 *  - 移动端：转为分卡片视图（外层 hidden md:block）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckIcon, MinusIcon } from "lucide-react"
import { useMemo } from "react"
import type { PlanEntitlementVO, SubscriptionPlanVO } from "@/lib/api/rest/billing/plans"
import { cn } from "@/lib/utils/cn"

const CYCLE_LABEL: Record<string, string> = {
  NONE: "",
  DAILY: "/天",
  MONTHLY: "/月",
  YEARLY: "/年"
}

export interface PlanCompareTableProps {
  plans: SubscriptionPlanVO[]
  /** 推荐套餐 code（高亮列） */
  recommendedCode?: string
}

interface EntitlementRow {
  code: string
  name: string
  values: Record<string, PlanEntitlementVO | null>
}

export function PlanCompareTable({ plans, recommendedCode }: PlanCompareTableProps) {
  // 收集所有 entitlement code，按出现顺序去重
  const rows = useMemo<EntitlementRow[]>(() => {
    const map = new Map<string, EntitlementRow>()
    for (const plan of plans) {
      for (const ent of plan.entitlements ?? []) {
        if (!map.has(ent.code)) {
          map.set(ent.code, {
            code: ent.code,
            name: ent.name,
            values: Object.fromEntries(plans.map((p) => [p.code, null]))
          })
        }
        const row = map.get(ent.code)
        if (row) row.values[plan.code] = ent
      }
    }
    return Array.from(map.values())
  }, [plans])

  if (plans.length === 0 || rows.length === 0) return null

  return (
    <div className="hidden md:block">
      <div className="overflow-x-auto rounded-2xl border bg-card shadow-sm">
        <table className="w-full text-sm">
          <thead className="sticky top-0 bg-muted/50 backdrop-blur-sm">
            <tr>
              <th className="px-5 py-4 text-left font-semibold">权益</th>
              {plans.map((p) => (
                <th
                  key={p.code}
                  className={cn(
                    "min-w-[120px] px-5 py-4 text-center font-semibold",
                    p.code === recommendedCode && "bg-primary/5 text-primary"
                  )}
                >
                  {p.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, idx) => (
              <tr key={row.code} className={cn("border-t", idx % 2 === 1 && "bg-muted/20")}>
                <td className="px-5 py-3 text-foreground/80">{row.name}</td>
                {plans.map((p) => (
                  <td
                    key={p.code}
                    className={cn(
                      "px-5 py-3 text-center",
                      p.code === recommendedCode && "bg-primary/5"
                    )}
                  >
                    <CellValue ent={row.values[p.code]} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function CellValue({ ent }: { ent: PlanEntitlementVO | null }) {
  if (!ent) {
    return <MinusIcon className="mx-auto size-4 text-muted-foreground/50" strokeWidth={1.75} />
  }
  if (ent.type === "BOOLEAN") {
    return (
      <CheckIcon
        className="mx-auto size-4 text-emerald-600 dark:text-emerald-400"
        strokeWidth={2.5}
      />
    )
  }
  if (ent.quota === -1) {
    return <span className="font-medium text-foreground">无限</span>
  }
  return (
    <span className="text-foreground/80">
      {ent.quota.toLocaleString()}
      {ent.unit ?? ""}
      <span className="text-muted-foreground">{CYCLE_LABEL[ent.resetCycle] ?? ""}</span>
    </span>
  )
}
