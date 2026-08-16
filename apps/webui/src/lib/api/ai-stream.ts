/**
 * 业务 AI POST SSE 适配器。
 *
 * 底层统一使用 Headless Assistant 安全事件解析器；`onChunk` 仅接收
 * MESSAGE_DELTA.payload.delta，不把原始 SSE data 当作文本。
 */

import {
  type AssistantSafeEvent,
  type AssistantSseOptions,
  postAssistantEventStream
} from "./headless-assistant"

export interface AiSseOptions extends AssistantSseOptions {
  onChunk: (text: string, event: AssistantSafeEvent) => void
}

/** 向业务生成接口发起 POST SSE 请求并分发结构化安全事件。 */
export async function postAiStream(path: string, body: unknown, opts: AiSseOptions): Promise<void> {
  await postAssistantEventStream(path, body, opts)
}
