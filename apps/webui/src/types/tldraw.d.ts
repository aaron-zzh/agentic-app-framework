/**
 * @tldraw/tldraw 类型声明存根
 * tldraw 尚未安装（计划 v2.0+ 引入），此文件提供最小类型声明避免编译错误
 */
declare module "@tldraw/tldraw" {
  import type { ComponentType, ReactNode } from "react"

  export interface TLBaseShape<Type extends string, Props extends object> {
    id: string
    type: Type
    x: number
    y: number
    props: Props
  }

  export type TLShapeUtilConstructor<T extends TLBaseShape<string, object>> = new () => BaseBoxShapeUtil<T>

  export class BaseBoxShapeUtil<T extends TLBaseShape<string, object>> {
    static type: string
    getDefaultProps(): T["props"]
    component(shape: T): ReactNode
    indicator(shape: T): ReactNode
    getSelectedShapes?(): T[]
  }

  export function HTMLContainer(props: { children: ReactNode; className?: string }): ReactNode

  export interface Editor {
    getCurrentPageShapes(): TLBaseShape<string, object>[]
    getCurrentPageShapeIds(): Set<string>
    getSelectedShapes(): TLBaseShape<string, object>[]
    createShape(shape: Partial<TLBaseShape<string, object>>): void
    selectAll(): void
    deleteShapes(ids: string[]): void
  }

  export interface TLStore {
    listen(fn: () => void): () => void
  }

  export function createTLStore(opts?: { shapeUtils?: TLShapeUtilConstructor<TLBaseShape<string, object>>[] }): TLStore
  export const defaultShapeUtils: TLShapeUtilConstructor<TLBaseShape<string, object>>[]

  export function Tldraw(props: {
    store?: TLStore
    shapeUtils?: TLShapeUtilConstructor<TLBaseShape<string, object>>[]
    onMount?: (editor: Editor) => void
    className?: string
    children?: ReactNode
    inferDarkMode?: boolean
  }): ReactNode

  export function exportToBlob(opts: { editor: Editor; format: string; ids?: string[]; opts?: { background?: boolean; padding?: number } }): Promise<Blob>
}
