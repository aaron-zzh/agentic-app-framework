/**
 * SmartButton——表单顶部统计快捷按钮（显示关联数据计数，点击跳转）
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { EntityDef } from "@/features/entity-engine/types"

interface SmartButtonsProps {
  entity: EntityDef
  record: Record<string, unknown>
}

/** 表单顶部 Smart Buttons */
export function SmartButtons({ entity, record }: SmartButtonsProps) {
  const buttons = entity.smartButtons
  if (!buttons?.length) return null

  return (
    <div className="flex flex-wrap gap-2">
      {buttons.map((btn) => {
        const count = record[btn.countField] as number | undefined
        // 替换 {id} 占位符
        const href = btn.linkTo.replace("{id}", String(record.id ?? ""))

        return (
          <Button key={btn.key} variant="outline" size="sm" asChild>
            <Link href={href}>
              <span>{btn.icon}</span>
              <span>{btn.label.replace("{count}", String(count ?? 0))}</span>
              {count !== undefined && count > 0 && (
                <Badge variant="secondary" className="ml-1">
                  {count}
                </Badge>
              )}
            </Link>
          </Button>
        )
      })}
    </div>
  )
}
