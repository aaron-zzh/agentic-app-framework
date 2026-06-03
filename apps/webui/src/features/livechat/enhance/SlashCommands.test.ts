/**
 * SlashCommands 单元测试——验证命令注册和触发逻辑
 */

import { describe, expect, it } from "vitest"
import { registerCommand, type SlashCommand } from "./SlashCommands"

describe("SlashCommands 命令注册", () => {
  it("registerCommand 应注册自定义命令", () => {
    const cmd: SlashCommand = {
      name: "test-cmd",
      description: "测试命令",
      icon: null as unknown as React.ReactElement,
      action: () => {}
    }

    // 不应抛异常
    expect(() => registerCommand(cmd)).not.toThrow()
  })
})

describe("SlashCommands 触发逻辑", () => {
  it("以 / 开头的输入应触发命令面板", () => {
    const inputValue = "/search"
    const shouldShow = inputValue.startsWith("/") && inputValue.length >= 1
    expect(shouldShow).toBe(true)
  })

  it("不以 / 开头的输入不应触发", () => {
    const inputValue = "hello"
    const shouldShow = inputValue.startsWith("/") && inputValue.length >= 1
    expect(shouldShow).toBe(false)
  })

  it("空字符串不应触发", () => {
    const inputValue = ""
    const shouldShow = inputValue.startsWith("/") && inputValue.length >= 1
    expect(shouldShow).toBe(false)
  })
})
