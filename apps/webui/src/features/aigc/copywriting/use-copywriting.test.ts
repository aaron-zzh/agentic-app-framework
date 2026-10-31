/**
 * 文案草稿流预览纯逻辑单元测试。
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import type { AssistantAgUiEvent } from "@/lib/api/assistant-agui"
import type { AafAiTaskEvent, AafAiTaskEventData } from "@/lib/api/rest/ai"
import {
  assistantDraftArtifact,
  copywritingDraftPreviewEvent,
  latestCopywritingDraftPreviewContent,
  reduceCopywritingStreamContent,
  resolveCopywritingDisplayContent
} from "./use-copywriting"

function customEvent(eventType: `aaf.${string}`, data: AafAiTaskEventData): AssistantAgUiEvent {
  const value: AafAiTaskEvent = {
    eventId: "event-1",
    taskId: "task-1",
    executionId: "execution-1",
    runId: "run-1",
    sessionId: "session-1",
    sequence: 1,
    eventOffset: 1,
    status: "STREAMING",
    type: eventType,
    version: 1,
    audience: "END_USER",
    delivery: "LIVE_BEST_EFFORT",
    data,
    createdAt: "2026-01-01T00:00:00.000Z"
  }
  return { type: "CUSTOM", runId: "run-1", name: eventType, value }
}

describe("copywritingDraftPreviewEvent", () => {
  it("仅接受 content.draft.upsert 的草稿预览参数事件", () => {
    const preview = copywritingDraftPreviewEvent(
      customEvent("aaf.tool.args_progress", {
        toolCallId: "tool-1",
        toolName: "content.draft.upsert",
        previewKind: "CONTENT_DRAFT",
        contentDelta: "参数预览"
      })
    )
    const otherTool = copywritingDraftPreviewEvent(
      customEvent("aaf.tool.args_progress", {
        toolCallId: "tool-2",
        toolName: "content.publish",
        previewKind: "CONTENT_DRAFT",
        contentDelta: "不能展示"
      })
    )
    const otherPreviewKind = copywritingDraftPreviewEvent(
      customEvent("aaf.tool.args_progress", {
        toolCallId: "tool-3",
        toolName: "content.draft.upsert",
        previewKind: "CONTENT_PUBLISH",
        contentDelta: "不能展示"
      })
    )

    expect(preview).toEqual({
      kind: "progress",
      toolCallId: "tool-1",
      contentDelta: "参数预览"
    })
    expect(otherTool).toBeNull()
    expect(otherPreviewKind).toBeNull()
  })
})

describe("文案草稿预览恢复", () => {
  it("恢复流优先保留最新参数预览，普通文本不得覆盖", () => {
    const preview = latestCopywritingDraftPreviewContent(
      new Map([
        ["tool-1", "旧预览"],
        ["tool-2", "草稿参数预览"]
      ])
    )
    const resumedText = reduceCopywritingStreamContent(
      { text: "", messageId: null },
      {
        type: "TEXT_MESSAGE_CONTENT",
        runId: "run-2",
        messageId: "message-2",
        delta: "恢复后的普通文本"
      }
    )

    expect(preview).toBe("草稿参数预览")
    expect(resolveCopywritingDisplayContent(preview, resumedText, "历史文案")).toBe("草稿参数预览")
  })
})

describe("assistantDraftArtifact", () => {
  it("从工具结果提取完整草稿文档版本", () => {
    const artifact = assistantDraftArtifact({
      type: "TOOL_CALL_RESULT",
      runId: "run-1",
      toolCallId: "tool-1",
      content: JSON.stringify({
        artifactState: "DRAFT",
        artifactId: 91,
        artifactVersionId: 92
      })
    })

    expect(artifact).toEqual({ documentId: 91, documentVersionId: 92 })
  })

  it("拒绝缺少有效文档版本的工具结果", () => {
    const artifact = assistantDraftArtifact({
      type: "TOOL_CALL_RESULT",
      runId: "run-1",
      toolCallId: "tool-1",
      content: JSON.stringify({ artifactState: "DRAFT", artifactId: 91 })
    })

    expect(artifact).toBeNull()
  })
})
