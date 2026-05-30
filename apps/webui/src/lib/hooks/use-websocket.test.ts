/**
 * use-websocket maxRetries 测试
 * 验证超过最大重连次数后不再重连
 */

import { act, renderHook } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useWebSocket } from "./use-websocket"

// Mock WebSocket
class MockWebSocket {
  static instances: MockWebSocket[] = []
  onopen: (() => void) | null = null
  onclose: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  readyState = 0

  constructor(_url: string) {
    MockWebSocket.instances.push(this)
  }

  send = vi.fn()
  close = vi.fn(() => {
    this.readyState = 3
  })

  /** 模拟连接关闭 */
  simulateClose() {
    this.onclose?.()
  }
}

describe("useWebSocket maxRetries", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    MockWebSocket.instances = []
    vi.stubGlobal("WebSocket", MockWebSocket)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it("超过 maxRetries 后停止重连并调用 onMaxRetriesReached", () => {
    const onMaxRetriesReached = vi.fn()

    renderHook(() =>
      useWebSocket({
        url: "ws://test",
        maxRetries: 3,
        onMaxRetriesReached
      })
    )

    // 初始连接
    expect(MockWebSocket.instances).toHaveLength(1)

    // 模拟 3 次断开重连
    for (let i = 0; i < 3; i++) {
      act(() => {
        MockWebSocket.instances[MockWebSocket.instances.length - 1].simulateClose()
      })
      // 推进定时器触发重连
      act(() => {
        vi.runOnlyPendingTimers()
      })
    }

    // 第 4 次断开——已达 maxRetries，不应再重连
    const countBefore = MockWebSocket.instances.length
    act(() => {
      MockWebSocket.instances[MockWebSocket.instances.length - 1].simulateClose()
    })
    // 推进定时器，不应有新连接
    act(() => {
      vi.runOnlyPendingTimers()
    })

    expect(MockWebSocket.instances).toHaveLength(countBefore)
    expect(onMaxRetriesReached).toHaveBeenCalledTimes(1)
  })
})
