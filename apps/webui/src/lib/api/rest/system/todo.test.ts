import { describe, expect, it, vi } from "vitest"

const { put } = vi.hoisted(() => ({ put: vi.fn() }))

vi.mock("../backend-client", () => ({
  backendApi: { put }
}))

import { todoApi } from "./todo"

describe("todoApi", () => {
  it("更新标题时应携带 expectedVersion", async () => {
    await todoApi.update(7, { title: "更新后的标题", expectedVersion: 3 })

    expect(put).toHaveBeenCalledWith(
      "/todos/7",
      { title: "更新后的标题", expectedVersion: 3 },
      { headers: { "X-Scope": "own" } }
    )
  })

  it("更新状态时应携带 expectedVersion", async () => {
    await todoApi.updateStatus(7, "done", 3)

    expect(put).toHaveBeenCalledWith(
      "/todos/7/status",
      { status: "done", expectedVersion: 3 },
      { headers: { "X-Scope": "own" } }
    )
  })
})
