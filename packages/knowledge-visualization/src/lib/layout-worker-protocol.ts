/**
 * 布局 Worker 的内部消息合同，仅传递可结构化克隆的渲染字段。
 * @author AaronZZH & Kiro
 */

import type { GraphPosition, LayoutOptions } from "./types"

export interface LayoutWorkerNode {
  readonly id: string
  readonly position?: GraphPosition
  readonly layer?: number
}

export interface LayoutWorkerEdge {
  readonly id: string
  readonly source: string
  readonly target: string
  readonly strength?: number
}

export interface LayoutWorkerRequest {
  readonly requestId: number
  readonly nodes: readonly LayoutWorkerNode[]
  readonly edges: readonly LayoutWorkerEdge[]
  readonly options: LayoutOptions
}

export type LayoutWorkerResponse =
  | {
      readonly requestId: number
      readonly positions: readonly (readonly [string, GraphPosition])[]
    }
  | {
      readonly requestId: number
      readonly error: string
    }
