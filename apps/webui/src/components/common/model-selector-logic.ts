/**
 * ModelSelector 纯选择逻辑：区分 AUTO 与具体模型，不依赖 React。
 * @author AaronZZH & Kiro
 */

import type { ModelOption } from "@/lib/hooks/use-model-selector"

/** ModelSelector 可选的自动选择项，不伪造具体模型元数据。 */
export interface ModelSelectorAutoOption {
  selected: boolean
  onSelect: () => void
  label: string
}

export type ModelSelectorSelection =
  | { type: "auto"; label: string }
  | { type: "model"; option: ModelOption; label: string }
  | { type: "none" }

/** 判断选择器是否至少有一个可展示选项。 */
export function hasModelSelectorChoices(
  options: ModelOption[],
  autoOption?: ModelSelectorAutoOption
): boolean {
  return options.length > 0 || autoOption !== undefined
}

/** 解析当前展示项，AUTO 与具体模型保持为不同类型。 */
export function resolveModelSelectorSelection(
  options: ModelOption[],
  value: string | null | undefined,
  autoOption?: ModelSelectorAutoOption
): ModelSelectorSelection {
  if (autoOption?.selected) {
    return { type: "auto", label: autoOption.label }
  }

  const option = options.find((candidate) => candidate.value === value)
  if (option) {
    return { type: "model", option, label: option.label }
  }

  return { type: "none" }
}
