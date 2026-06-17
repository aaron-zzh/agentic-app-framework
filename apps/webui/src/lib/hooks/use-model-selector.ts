import { useCallback, useEffect, useRef, useState } from "react"
import type { AiModelVO } from "@/lib/api/rest/ai/ai-model"
import { useAiModels } from "@/lib/queries/use-ai-models"

export interface ModelOption {
  value: string
  label: string
  meta: AiModelVO
}

interface UseModelSelectorOptions {
  value?: string
  onChange?: (modelId: string, model: AiModelVO) => void
  autoSelect?: boolean
}

export function useModelSelector(capability: string, opts: UseModelSelectorOptions = {}) {
  const { value, onChange, autoSelect = true } = opts

  const { data: allModels = [], isLoading } = useAiModels(capability)

  const options: ModelOption[] = allModels.map((m) => ({
    value: m.modelId,
    label: m.displayName,
    meta: m
  }))

  const isControlled = value !== undefined
  const [internalValue, setInternalValue] = useState<string>(value ?? "")
  const modelId = isControlled ? value : internalValue

  // capability 切换时清空，让 autoSelect 重新选第一个
  const prevCapability = useRef(capability)
  if (prevCapability.current !== capability) {
    prevCapability.current = capability
    if (!isControlled) setInternalValue("")
  }

  const setModelId = useCallback(
    (id: string) => {
      setInternalValue(id)
      if (onChange) {
        const model = options.find((o) => o.value === id)
        if (model) onChange(id, model.meta)
      }
    },
    [onChange, options]
  )

  // 未选中时自动选第一个
  const firstOption = options[0]
  useEffect(() => {
    if (autoSelect && !internalValue && firstOption) {
      setInternalValue(firstOption.value)
      onChange?.(firstOption.value, firstOption.meta)
    }
  }, [autoSelect, internalValue, firstOption, onChange])

  const currentModel = options.find((o) => o.value === modelId)?.meta

  return { options, modelId, setModelId, currentModel, isLoading }
}
