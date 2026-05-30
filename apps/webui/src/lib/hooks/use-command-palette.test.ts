/**
 * use-command-palette.ts 单元测试
 */

import { afterEach, describe, expect, it, vi } from "vitest"
import { commandRegistry } from "./use-command-palette"

// mock localStorage
const storage: Record<string, string> = {}
vi.stubGlobal("localStorage", {
  getItem: (key: string) => storage[key] ?? null,
  setItem: (key: string, value: string) => { storage[key] = value },
  removeItem: (key: string) => { delete storage[key] }
})

describe("commandRegistry", () => {
  afterEach(() => {
    for (const cmd of commandRegistry.getAll()) {
      commandRegistry.unregister(cmd.id)
    }
  })

  it("register 应添加命令", () => {
    commandRegistry.register({ id: "test", label: "测试", group: "命令", action: () => {} })

    expect(commandRegistry.getAll()).toHaveLength(1)
    expect(commandRegistry.getAll()[0].id).toBe("test")
  })

  it("unregister 应移除命令", () => {
    commandRegistry.register({ id: "rm", label: "删除", group: "命令", action: () => {} })
    commandRegistry.unregister("rm")

    expect(commandRegistry.getAll()).toHaveLength(0)
  })

  it("registerAll 应批量添加", () => {
    commandRegistry.registerAll([
      { id: "a", label: "A", group: "g", action: () => {} },
      { id: "b", label: "B", group: "g", action: () => {} }
    ])

    expect(commandRegistry.getAll()).toHaveLength(2)
  })

  it("getSnapshot 应返回当前命令列表", () => {
    commandRegistry.register({ id: "x", label: "X", group: "g", action: () => {} })

    const snapshot = commandRegistry.getSnapshot()

    expect(snapshot.some((c) => c.id === "x")).toBe(true)
  })
})
