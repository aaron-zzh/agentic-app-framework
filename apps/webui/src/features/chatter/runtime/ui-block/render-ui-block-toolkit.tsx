"use client"

/**
 * render_ui_block 工具的 tool-call 消息 part 渲染器（AAF-114 官方模式改造）。
 *
 * 对齐官方 with-generative-ui 示例的 render_gui 工具桥接模式：模型调用后端 render_ui_block 工具，
 * 结果通过 assistant-ui 原生 tool-call 消息 part 到达前端，本渲染器解析并渲染对应卡片。
 * 不再依赖 AG-UI CUSTOM 事件旁路（原 ui-block-projector.ts / agent-run-store.uiBlocks 已移除）。
 *
 * 前端只信任经过 {@link parseAafUiBlock} 结构校验的结果——工具执行时后端已校验过一次
 * （RenderUiBlockTool），前端仍独立复核，双重防线，任一方绕过都不会渲染非法结构。
 *
 * @author AaronZZH & Kiro
 */

import { defineToolkit } from "@assistant-ui/react"
import { AafUiBlockCard } from "./AafUiBlockCard"
import { parseAafUiBlock } from "./aaf-ui-block"

/** result 到达前端时可能是字符串化 JSON，也可能已被上游解析为对象——两种形态都要处理。 */
function parseToolResult(result: unknown): unknown {
  if (typeof result !== "string") return result
  try {
    return JSON.parse(result)
  } catch {
    return null
  }
}

const renderUiBlock = ({ result }: { result?: unknown }) => {
  const block = parseAafUiBlock(parseToolResult(result))
  if (!block) return null
  return <AafUiBlockCard block={block} />
}

export const renderUiBlockToolkit = defineToolkit({
  render_ui_block: { type: "backend", render: renderUiBlock }
})
