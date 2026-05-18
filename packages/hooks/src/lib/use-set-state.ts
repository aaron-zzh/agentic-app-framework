import { useCallback, useState } from "react"

/**
 * 对象状态管理 Hook，类似 class 组件的 setState（浅合并）
 *
 * @example
 * const { state, setState } = useSetState({ user: null, loading: true });
 * setState({ loading: false }); // 只更新 loading，保留 user
 */
export function useSetState<T extends Record<string, unknown>>(initialState: T) {
  const [state, set] = useState<T>(initialState)

  const setState = useCallback((newState: Partial<T> | ((prev: T) => Partial<T>)) => {
    set((prev) => ({
      ...prev,
      ...(typeof newState === "function" ? newState(prev) : newState)
    }))
  }, [])

  return { state, setState }
}
