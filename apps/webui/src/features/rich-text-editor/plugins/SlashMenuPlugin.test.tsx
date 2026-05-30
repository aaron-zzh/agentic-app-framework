/**
 * SlashMenuPlugin.tsx 单元测试——验证插件导出
 */

import { describe, expect, it } from "vitest"
import { SlashMenuPlugin } from "./SlashMenuPlugin"

describe("SlashMenuPlugin", () => {
  it("应导出 SlashMenuPlugin 函数", () => {
    expect(SlashMenuPlugin).toBeDefined()
    expect(typeof SlashMenuPlugin).toBe("function")
  })
})
