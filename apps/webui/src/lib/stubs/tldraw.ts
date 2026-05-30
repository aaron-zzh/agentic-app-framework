/**
 * @tldraw/tldraw 运行时存根
 * tldraw 尚未安装（计划 v2.0+ 引入），此文件提供空实现避免构建错误。
 * 画板功能在 tldraw 安装后替换为真实实现。
 */

/* eslint-disable @typescript-eslint/no-unused-vars */

import type { ReactNode } from "react"

export interface TLBaseShape<Type extends string, Props extends object> {
  id: string
  type: Type
  x: number
  y: number
  props: Props
}

export type TLShapeUtilConstructor<_T extends TLBaseShape<string, object>> = new (...args: unknown[]) => BaseBoxShapeUtil<TLBaseShape<string, object>>

export class BaseBoxShapeUtil<T extends TLBaseShape<string, object>> {
  static type: string
  getDefaultProps(): T["props"] { return {} as T["props"] }
  component(_shape: T): ReactNode { return null }
  indicator(_shape: T): ReactNode { return null }
}

export function HTMLContainer({ children }: { children: ReactNode; className?: string }): ReactNode {
  return children
}

export interface Editor {
  getCurrentPageShapes(): TLBaseShape<string, object>[]
  getCurrentPageShapeIds(): Set<string>
  getSelectedShapes(): TLBaseShape<string, object>[]
  createShape(_shape: Partial<TLBaseShape<string, object>>): void
  selectAll(): void
  deleteShapes(_ids: string[]): void
}

export interface TLStore {
  listen(_fn: () => void): () => void
}

export function createTLStore(_opts?: { shapeUtils?: TLShapeUtilConstructor<TLBaseShape<string, object>>[] }): TLStore {
  return { listen: () => () => {} }
}

export const defaultShapeUtils: TLShapeUtilConstructor<TLBaseShape<string, object>>[] = []

export function Tldraw(_props: {
  store?: TLStore
  shapeUtils?: TLShapeUtilConstructor<TLBaseShape<string, object>>[]
  onMount?: (editor: Editor) => void
  className?: string
  children?: ReactNode
  inferDarkMode?: boolean
}): ReactNode {
  return null
}

export async function exportToBlob(_opts: { editor: Editor; format: string; ids?: string[]; opts?: { background?: boolean; padding?: number } }): Promise<Blob> {
  return new Blob()
}
