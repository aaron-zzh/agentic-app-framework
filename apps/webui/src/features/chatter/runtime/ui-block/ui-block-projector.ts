/**
 * AG-UI CUSTOM 事件 → AafUiBlock 的唯一投影适配器（AAF-114 #11407）。
 *
 * 背景：react-ag-ui 0.0.41 的 RunAggregator 对 CUSTOM 事件只 debug 忽略，不会自动生成
 * assistant-ui message part；AAF 现有 `ui_block` CUSTOM 事件名已用于 `aigc_task` 卡片
 * （见 ag-ui-runtime.tsx），本适配器在同一事件名下新增 `aaf_ui_block` 判别分支，
 * 复用现有 Zustand 瞬时展示状态通道，不写入 assistant-ui 消息历史（那需要 #11411
 * 的完整消息信封才能做到语义正确的持久化投影）。
 *
 * 去重：以 eventId 为键；同一 eventId 的事件只投影一次。
 * 大小限制：单个 payload 序列化后超过 32KB 视为协议违规，返回 unknown。
 *
 * @author AaronZZH & Kiro
 */

import { type AafUiBlock, parseAafUiBlock } from "./aaf-ui-block"

const MAX_PAYLOAD_BYTES = 32 * 1024

export type CustomEventProjection =
  | { kind: "ui-block"; block: AafUiBlock }
  | { kind: "duplicate" }
  | { kind: "unknown"; rawName: string; reason: string }

/** 投影输入：AG-UI CUSTOM 事件的 name + value，eventId 用于去重（可能缺失）。 */
export interface CustomEventEnvelope {
  name: string
  eventId?: string
  value: unknown
}

/**
 * 已投影 eventId 集合——按 Chatter runtime 实例持有，用于跨事件去重。
 *
 * 用类而非模块级单例，避免多个并发对话实例（如多个浮窗）共享去重状态。
 */
export class UiBlockProjector {
  private readonly seenEventIds = new Set<string>()

  /** 投影一个 CUSTOM 事件；仅处理 `ui_block` 事件名下 `uiType === "aaf_ui_block"` 的 payload。 */
  project(envelope: CustomEventEnvelope): CustomEventProjection | null {
    if (envelope.name !== "ui_block") return null
    const raw = envelope.value
    if (typeof raw !== "object" || raw === null) {
      return { kind: "unknown", rawName: envelope.name, reason: "payload 不是对象" }
    }
    const payload = raw as Record<string, unknown>
    if (payload.uiType !== "aaf_ui_block") return null

    if (envelope.eventId) {
      if (this.seenEventIds.has(envelope.eventId)) {
        return { kind: "duplicate" }
      }
      this.seenEventIds.add(envelope.eventId)
    }

    const size = safeByteLength(payload.block)
    if (size > MAX_PAYLOAD_BYTES) {
      return {
        kind: "unknown",
        rawName: envelope.name,
        reason: `payload 超过 ${MAX_PAYLOAD_BYTES} 字节`
      }
    }

    const block = parseAafUiBlock(payload.block)
    if (!block) {
      return {
        kind: "unknown",
        rawName: envelope.name,
        reason: "block 字段结构不合法或版本不受支持"
      }
    }
    return { kind: "ui-block", block }
  }

  /** 清空去重记录——新会话/新 run 开始时调用，避免跨会话误判重复。 */
  reset(): void {
    this.seenEventIds.clear()
  }
}

function safeByteLength(value: unknown): number {
  try {
    return new TextEncoder().encode(JSON.stringify(value)).length
  } catch {
    return Number.POSITIVE_INFINITY
  }
}
