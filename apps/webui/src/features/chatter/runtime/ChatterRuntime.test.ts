/**
 * ChatterRuntime 纯状态单元测试
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import { type ChatterTarget, DEFAULT_TASK_MODEL_SELECTION } from "@/features/chatter/types"
import {
  buildChatterInitialState,
  DEFAULT_CHATTER_ASSISTANT_ID,
  resolveChatterAguiPath
} from "./chatter-runtime-state"

const aiTarget: ChatterTarget = { type: "ai" }

describe("Chatter 任务模型 initialState", () => {
  it("默认任务模型选择应为 AUTO", () => {
    expect(DEFAULT_TASK_MODEL_SELECTION).toEqual({ mode: "AUTO" })
  })

  it("AUTO 应发送固定 Assistant 身份且不伪造 modelId", () => {
    const state = buildChatterInitialState({
      target: aiTarget,
      isAuthenticated: true,
      taskModelSelection: DEFAULT_TASK_MODEL_SELECTION
    })

    expect(state).toMatchObject({
      assistantId: DEFAULT_CHATTER_ASSISTANT_ID,
      taskModelSelection: { mode: "AUTO" }
    })
    expect(state).not.toHaveProperty("modelId")
  })

  it("EXPLICIT 应在 taskModelSelection 中发送业务 modelId", () => {
    const state = buildChatterInitialState({
      target: aiTarget,
      isAuthenticated: true,
      taskModelSelection: { mode: "EXPLICIT", modelId: "qwen-plus" }
    })

    expect(state).toMatchObject({
      assistantId: DEFAULT_CHATTER_ASSISTANT_ID,
      taskModelSelection: { mode: "EXPLICIT", modelId: "qwen-plus" }
    })
  })

  it("未启用任务模型选择的其他 Chatter 场景不应注入该状态", () => {
    const state = buildChatterInitialState({
      target: { type: "ai", agentRole: "customer-service" },
      isAuthenticated: false
    })

    expect(state).toMatchObject({ agentRole: "customer-service" })
    expect(state).not.toHaveProperty("assistantId")
    expect(state).not.toHaveProperty("taskModelSelection")
  })
})

describe("Chatter AG-UI 路径", () => {
  it("已登录 AI 应复用 /agui/run", () => {
    expect(resolveChatterAguiPath(aiTarget, true)).toBe("/agui/run")
  })

  it("匿名 AI 应保留按角色区分的路径", () => {
    expect(resolveChatterAguiPath({ type: "ai", agentRole: "customer-service" }, false)).toBe(
      "/agui/run/customer-service"
    )
  })
})
