import { useCallback, useState } from "react"

/**
 * 布尔状态管理 Hook
 *
 * @example
 * const { value: open, onTrue: onOpen, onFalse: onClose, onToggle } = useBoolean();
 */
export function useBoolean(defaultValue = false) {
  const [value, setValue] = useState(defaultValue)

  const onTrue = useCallback(() => setValue(true), [])
  const onFalse = useCallback(() => setValue(false), [])
  const onToggle = useCallback(() => setValue((v) => !v), [])

  return { value, setValue, onTrue, onFalse, onToggle }
}
