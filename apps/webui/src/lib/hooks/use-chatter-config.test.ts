/**
 * use-chatter-config 单元测试——验证 effect 不会因 defaultConfig 引用变化而无限循环
 * @author AaronZZH & Kiro
 */

import { act, renderHook } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useChatterStore } from "@/lib/store/chatter-store"

// mock request 防止 syncToRemote 发出真实 XHR
vi.mock("@/lib/api/rest/entity/crud", () => ({
  request: vi.fn().mockResolvedValue(null)
}))

// mock loadRemoteConfig
vi.mock("@/lib/store/chatter-store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/store/chatter-store")>()
  return {
    ...actual,
    loadRemoteConfig: vi.fn().mockResolvedValue(null)
  }
})

import { loadRemoteConfig } from "@/lib/store/chatter-store"
import { useChatterConfig } from "./use-chatter-config"

describe("useChatterConfig", () => {
  beforeEach(() => {
    // 清空 store
    useChatterStore.setState({ configs: {}, currentPageId: null, open: false })
    // 重置 mock 调用记录但保留实现
    vi.mocked(loadRemoteConfig).mockClear()
    vi.mocked(loadRemoteConfig).mockResolvedValue(null)
  })

  it("多次重渲染传入新 defaultConfig 对象引用时，effect 不会重复触发", async () => {
    const mockedLoad = vi.mocked(loadRemoteConfig)

    // 每次渲染传入新对象引用（模拟调用方未 memoize）
    const { rerender } = renderHook(
      ({ pageId }) => useChatterConfig(pageId, { preset: "ai", open: false }),
      { initialProps: { pageId: "page-1" } }
    )

    // 等待异步 effect 完成
    await act(async () => {
      await Promise.resolve()
    })

    // 第一次渲染应触发一次 loadRemoteConfig
    expect(mockedLoad).toHaveBeenCalledTimes(1)

    // 重渲染多次（传入新对象引用）
    rerender({ pageId: "page-1" })
    rerender({ pageId: "page-1" })
    rerender({ pageId: "page-1" })

    await act(async () => {
      await Promise.resolve()
    })

    // 不应再次触发（pageId 未变，configs 已有缓存）
    expect(mockedLoad).toHaveBeenCalledTimes(1)
  })

  it("pageId 变化时应重新加载配置", async () => {
    const mockedLoad = vi.mocked(loadRemoteConfig)

    const { rerender } = renderHook(
      ({ pageId }) => useChatterConfig(pageId, { preset: "ai", open: false }),
      { initialProps: { pageId: "page-1" } }
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(mockedLoad).toHaveBeenCalledTimes(1)
    expect(mockedLoad).toHaveBeenCalledWith("page-1")

    // 切换 pageId
    rerender({ pageId: "page-2" })

    await act(async () => {
      await Promise.resolve()
    })

    expect(mockedLoad).toHaveBeenCalledTimes(2)
    expect(mockedLoad).toHaveBeenCalledWith("page-2")
  })

  it("本地有缓存时不请求后端", async () => {
    const mockedLoad = vi.mocked(loadRemoteConfig)

    // 预设缓存
    useChatterStore.setState({
      configs: { "cached-page": { preset: "ai", open: true } }
    })

    renderHook(() => useChatterConfig("cached-page", { preset: "ai", open: false }))

    await act(async () => {
      await Promise.resolve()
    })

    expect(mockedLoad).not.toHaveBeenCalled()
  })
})
