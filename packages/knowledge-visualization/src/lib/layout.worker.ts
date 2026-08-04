/// <reference lib="webworker" />

/**
 * 知识图布局 Worker：隔离力导向计算，避免阻塞 React 与指针交互。
 * @author AaronZZH & Kiro
 */

import { resolveGraphLayout } from "./layout"
import type { LayoutWorkerRequest, LayoutWorkerResponse } from "./layout-worker-protocol"

const workerScope = self as unknown as DedicatedWorkerGlobalScope

workerScope.addEventListener("message", (event: MessageEvent<LayoutWorkerRequest>) => {
  const request = event.data
  let response: LayoutWorkerResponse

  try {
    const positions = resolveGraphLayout(
      {
        nodes: request.nodes.map((node) => ({ ...node, label: node.id })),
        edges: request.edges
      },
      request.options
    )
    response = {
      requestId: request.requestId,
      positions: Array.from(positions.entries())
    }
  } catch (cause) {
    response = {
      requestId: request.requestId,
      error: cause instanceof Error ? cause.message : String(cause)
    }
  }

  workerScope.postMessage(response)
})
