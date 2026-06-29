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
  defaultValue?: string
}

export function useModelSelector(capability: string, opts: UseModelSelectorOptions = {}) {
  const { value, onChange, autoSelect = true, defaultValue } = opts

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

  // 未选中时自动选：优先 defaultValue，找不到则选第一个
  const firstOption = options[0]
  const defaultOption = defaultValue ? options.find((o) => o.value === defaultValue) : undefined
  useEffect(() => {
    if (autoSelect && !internalValue && (defaultOption ?? firstOption)) {
      const target = defaultOption ?? firstOption
      setInternalValue(target.value)
      onChange?.(target.value, target.meta)
    }
  }, [autoSelect, internalValue, defaultOption, firstOption, onChange])

  const currentModel = options.find((o) => o.value === modelId)?.meta

  return { options, modelId, setModelId, currentModel, isLoading }
}
