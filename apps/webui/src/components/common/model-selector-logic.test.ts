/**
 * ModelSelector AUTO 与具体模型选择逻辑单元测试。
 * @author AaronZZH & Kiro
 */

import { describe, expect, it, vi } from "vitest"
import {
  hasModelSelectorChoices,
  type ModelSelectorAutoOption,
  resolveModelSelectorSelection
} from "./model-selector-logic"

const autoOption: ModelSelectorAutoOption = {
  selected: true,
  onSelect: vi.fn(),
  label: "任务模型 · 自动选择"
}

describe("ModelSelector 选择逻辑", () => {
  it("无具体模型但存在 AUTO 时仍应显示", () => {
    expect(hasModelSelectorChoices([], autoOption)).toBe(true)
    expect(resolveModelSelectorSelection([], null, autoOption)).toEqual({
      type: "auto",
      label: "任务模型 · 自动选择"
    })
  })

  it("未提供 AUTO 且无具体模型时不应显示", () => {
    expect(hasModelSelectorChoices([])).toBe(false)
    expect(resolveModelSelectorSelection([], null)).toEqual({ type: "none" })
  })
})
