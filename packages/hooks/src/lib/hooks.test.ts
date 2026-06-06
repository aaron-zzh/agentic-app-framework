import { act, renderHook } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { useBoolean, useSetState, useTabs } from "../index"

describe("useTabs", () => {
  it("应返回默认值", () => {
    const { result } = renderHook(() => useTabs("tab1"))
    expect(result.current.value).toBe("tab1")
  })

  it("onChange 应切换值", () => {
    const { result } = renderHook(() => useTabs("tab1"))
    act(() => result.current.onChange("tab2"))
    expect(result.current.value).toBe("tab2")
  })
})

describe("useBoolean", () => {
  it("默认值为 false", () => {
    const { result } = renderHook(() => useBoolean())
    expect(result.current.value).toBe(false)
  })

  it("onTrue/onFalse/onToggle", () => {
    const { result } = renderHook(() => useBoolean())
    act(() => result.current.onTrue())
    expect(result.current.value).toBe(true)
    act(() => result.current.onFalse())
    expect(result.current.value).toBe(false)
    act(() => result.current.onToggle())
    expect(result.current.value).toBe(true)
  })
})

describe("useSetState", () => {
  it("应浅合并状态", () => {
    const { result } = renderHook(() => useSetState({ a: 1, b: 2 }))
    act(() => result.current.setState({ b: 3 }))
    expect(result.current.state).toEqual({ a: 1, b: 3 })
  })

  it("支持函数式更新", () => {
    const { result } = renderHook(() => useSetState({ count: 0 }))
    act(() => result.current.setState((prev) => ({ count: prev.count + 1 })))
    expect(result.current.state.count).toBe(1)
  })
})
