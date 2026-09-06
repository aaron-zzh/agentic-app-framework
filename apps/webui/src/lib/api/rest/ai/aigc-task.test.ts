/**
 * 公共 AIGC Task 提交边界测试。
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock("../entity/crud", () => ({ request }))

import { aigcTaskApi } from "./aigc-task"

describe("aigcTaskApi", () => {
  beforeEach(() => request.mockReset().mockResolvedValue(1))

  it.each([
    ["image", "IMAGE", () => aigcTaskApi.generateImage({ prompt: "image" })],
    ["video", "VIDEO", () => aigcTaskApi.generateVideo({ prompt: "video" })],
    ["music", "MUSIC", () => aigcTaskApi.generateMusic({ prompt: "music", gender: "female" })],
    ["voice", "VOICE", () => aigcTaskApi.generateVoice({ prompt: "voice", voiceId: "voice-1" })],
    [
      "model-3d",
      "MODEL_3D",
      () => aigcTaskApi.generateModel3d({ prompt: "model", textureQuality: "detailed" })
    ]
  ] as const)("%s submit body 不携带 projectId", async (_mode, type, submit) => {
    await submit()
    expect(request).toHaveBeenCalledWith(
      "/aigc/tasks/submit",
      expect.objectContaining({ method: "POST" })
    )
    const init = request.mock.calls[0]?.[1] as RequestInit
    const body = JSON.parse(String(init.body)) as Record<string, unknown>
    expect(body.type).toBe(type)
    expect(body).not.toHaveProperty("projectId")
  })
})
