/**
 * AafUiBlock 渲染组件——INFO_CARD 的纯展示实现（AAF-114 #11408 第二版收窄）。
 *
 * 由 render_ui_block 工具的 tool-call 消息 part 触发渲染（见 render-ui-block-toolkit.tsx）。
 * 原 CHOICE/FORM 渲染已移除，改为 ClarificationInterruptPanel + SchemaForm 处理
 * （复用标准 AG-UI interrupt/resume 协议，不再由 AafUiBlock 承载）。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { AafUiBlock } from "@/features/chatter/runtime/ui-block/aaf-ui-block"

export function InfoCard({ block }: { block: AafUiBlock }) {
  return (
    <div className="my-1 rounded-lg border bg-card p-3">
      <p className="font-medium text-sm">{block.title}</p>
      {block.description && (
        <p className="mt-0.5 text-muted-foreground text-xs">{block.description}</p>
      )}
      {block.items && block.items.length > 0 && (
        <dl className="mt-2 space-y-1">
          {block.items.map((item, i) => (
            <div key={`${item.label}-${i}`} className="flex justify-between gap-2 text-xs">
              <dt className="text-muted-foreground">{item.label}</dt>
              <dd className="text-right">{item.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}

/** 按 block.type 分发；当前只有 INFO_CARD 一种类型，保留分发函数便于未来扩展。 */
export function AafUiBlockCard({ block }: { block: AafUiBlock }) {
  switch (block.type) {
    case "INFO_CARD":
      return <InfoCard block={block} />
    default:
      return null
  }
}
