/**
 * 看板卡片模板——根据配置渲染自定义字段布局、封面、快捷操作
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"

import type { EntityDef } from "@/lib/types/entity/entity"
import type { KanbanCardTemplate as CardTemplateConfig } from "@/lib/types/entity/views"
import { cn } from "@/lib/utils/cn"

interface KanbanCardTemplateProps {
  record: Record<string, unknown>
  entity: EntityDef
  template: CardTemplateConfig
}

/** 自定义卡片模板渲染器 */
export function KanbanCardTemplate({ record, entity, template }: KanbanCardTemplateProps) {
  const [showActions, setShowActions] = useState(false)
  const { displayFields, layout, coverField, quickActions } = template
  const { kanbanView, fields } = entity
  const cardTitle = kanbanView?.cardTitle ?? ""

  /** 获取字段标签 */
  function getFieldLabel(name: string): string {
    const field = fields.find((f) => "name" in f && f.name === name)
    return field && "label" in field ? (field.label ?? name) : name
  }

  return (
    // biome-ignore lint/a11y/useSemanticElements: 卡片容器需要 div 布局
    <div
      className="relative"
      role="group"
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      {/* 封面图片 */}
      {coverField && !!record[coverField] && (
        // biome-ignore lint/performance/noImgElement: 动态 URL，next/image 不适用
        <img
          src={String(record[coverField])}
          alt=""
          className="-mx-3 -mt-3 mb-2 h-24 w-[calc(100%+1.5rem)] rounded-t-md object-cover"
        />
      )}

      {/* 标题 */}
      <p className="font-medium text-sm leading-tight">{String(record[cardTitle] ?? "")}</p>

      {/* 自定义字段展示 */}
      {displayFields && displayFields.length > 0 && (
        <div
          className={cn("mt-2 gap-1", layout === "compact" ? "flex flex-wrap" : "flex flex-col")}
        >
          {displayFields.map((fieldName) => {
            const value = record[fieldName]
            if (value == null || value === "") return null
            return (
              <div key={fieldName} className="text-muted-foreground text-xs">
                {layout !== "compact" && (
                  <span className="text-muted-foreground/70">{getFieldLabel(fieldName)}: </span>
                )}
                <span>{String(value)}</span>
              </div>
            )
          })}
        </div>
      )}

      {/* 悬浮快捷操作按钮 */}
      {showActions && quickActions && quickActions.length > 0 && (
        <div className="absolute top-1 right-1 flex gap-1">
          {quickActions.map((action) => (
            <button
              key={action}
              type="button"
              className="rounded bg-background/90 px-1.5 py-0.5 text-xs shadow-sm hover:bg-accent"
              title={action}
            >
              {action}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
