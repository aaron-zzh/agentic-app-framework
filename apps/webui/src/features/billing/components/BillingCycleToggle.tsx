/**
 * BillingCycleToggle——按月/按年订阅切换
 *
 * 使用 segmented control 风格，激活按钮带阴影 + 强对比；
 * 年付按钮带"省更多"徽标，不依赖绝对定位以避免缩放变形。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

export interface BillingCycleToggleProps {
  value: "monthly" | "yearly"
  onChange: (value: "monthly" | "yearly") => void
}

export function BillingCycleToggle({ value, onChange }: BillingCycleToggleProps) {
  return (
    <div className="flex justify-center">
      <div
        role="radiogroup"
        aria-label="订阅周期"
        className="inline-flex items-center rounded-full border bg-muted p-1"
      >
        <CycleButton
          checked={value === "monthly"}
          onClick={() => onChange("monthly")}
          label="按月订阅"
        />
        <CycleButton
          checked={value === "yearly"}
          onClick={() => onChange("yearly")}
          label="按年订阅"
          badge="省更多"
        />
      </div>
    </div>
  )
}

interface CycleButtonProps {
  checked: boolean
  onClick: () => void
  label: string
  badge?: string
}

function CycleButton({ checked, onClick, label, badge }: CycleButtonProps) {
  return (
    <Button
      role="radio"
      aria-checked={checked}
      variant="ghost"
      size="sm"
      onClick={onClick}
      className={cn(
        "h-9 gap-2 rounded-full px-5 font-medium text-sm transition-all",
        checked
          ? "bg-background text-foreground shadow-sm hover:bg-background"
          : "text-muted-foreground hover:bg-transparent hover:text-foreground"
      )}
    >
      {label}
      {badge && (
        <span
          className={cn(
            "rounded-full px-1.5 py-0.5 font-semibold text-[10px] leading-none",
            checked
              ? "bg-amber-500 text-white"
              : "bg-amber-500/15 text-amber-600 dark:text-amber-400"
          )}
        >
          {badge}
        </span>
      )}
    </Button>
  )
}
