/**
 * 实体自身的 AI 生成状态呈现。
 *
 * <p>状态由服务端在实体投影里给出（如文档引用的 `generationStatus`），与触发方式无关，任务终结后仍保留；
 * 前端只做文案与呈现映射，不从任务范围里拼装。
 * @author AaronZZH & Kiro
 */

import type { AigcExecutionStatus } from "@/lib/api/rest/ai/aigc"

/** 最近一次生成执行的状态文案。 */
const RUN_STATUS_LABELS: Record<AigcExecutionStatus, string> = {
  PENDING_BIND: "排队中",
  PENDING: "排队中",
  RUNNING: "生成中",
  PARTIALLY_SUCCEEDED: "部分生成",
  SUCCEEDED: "已生成候选",
  FAILED: "生成失败",
  CANCELED: "已取消"
}

export interface GenerationStatusPresentation {
  /** 用户可见文案。 */
  label: string
  /** 仍在进行中，调用方据此禁用冲突的写入口。 */
  active: boolean
  /** 失败态单独标记，便于用危险色呈现。 */
  failed: boolean
}

/** 收窄服务端返回的状态字符串，未识别的状态不展示。 */
function isRunStatus(value: string): value is AigcExecutionStatus {
  return value in RUN_STATUS_LABELS
}

/**
 * 把服务端给的执行状态转成可见文案；从未生成过（`null`）时返回 `null`，调用方不显示徽标。
 *
 * @example
 * const generation = presentGenerationStatus(reference.generationStatus)
 */
export function presentGenerationStatus(
  status: string | null | undefined
): GenerationStatusPresentation | null {
  if (!status || !isRunStatus(status)) return null
  const pending = status === "PENDING_BIND" || status === "PENDING"
  return {
    label: RUN_STATUS_LABELS[status],
    active: pending || status === "RUNNING",
    failed: status === "FAILED"
  }
}
