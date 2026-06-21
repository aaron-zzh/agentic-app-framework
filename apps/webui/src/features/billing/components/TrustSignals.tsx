/**
 * TrustSignals——pricing 页 trust 元素
 *
 * 在套餐卡片上方展示 3-4 条转化保障信息，提升下单信心。
 *
 * @author AaronZZH & Kiro
 */

import { CheckIcon } from "lucide-react"

const SIGNALS = ["7 天无理由退款", "支持月度取消", "数据安全保障", "随时升级 / 降级"]

export function TrustSignals() {
  return (
    <ul className="mx-auto flex max-w-3xl flex-wrap items-center justify-center gap-x-6 gap-y-2 text-[12px] text-muted-foreground">
      {SIGNALS.map((s) => (
        <li key={s} className="inline-flex items-center gap-1.5">
          <CheckIcon
            className="size-3.5 shrink-0 text-emerald-600 dark:text-emerald-400"
            strokeWidth={2.5}
          />
          {s}
        </li>
      ))}
    </ul>
  )
}
