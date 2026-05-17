/**
 * ImageNode——Lexical 自定义图片节点
 * @author AaronZZH & Kiro
 */

"use client"

import {
  $applyNodeReplacement,
  DecoratorNode,
  type DOMConversionMap,
  type DOMConversionOutput,
  type DOMExportOutput,
  type EditorConfig,
  type LexicalNode,
  type NodeKey,
  type SerializedLexicalNode,
  type Spread
} from "lexical"
import type { JSX } from "react"
import { lazy, Suspense } from "react"

// 懒加载图片组件，避免 SSR 问题
const ImageComponent = lazy(() => import("./ImageComponent"))

export type SerializedImageNode = Spread<
  { src: string; alt: string; width?: number; height?: number },
  SerializedLexicalNode
>

export class ImageNode extends DecoratorNode<JSX.Element> {
  __src: string
  __alt: string
  __width?: number
  __height?: number

  static override getType(): string {
    return "image"
  }

  static override clone(node: ImageNode): ImageNode {
    return new ImageNode(node.__src, node.__alt, node.__width, node.__height, node.__key)
  }

  static override importJSON(data: SerializedImageNode): ImageNode {
    return $createImageNode(data.src, data.alt, data.width, data.height)
  }

  static override importDOM(): DOMConversionMap {
    return {
      img: () => ({
        conversion: (el: HTMLElement): DOMConversionOutput => {
          const img = el as HTMLImageElement
          return {
            node: $createImageNode(
              img.src,
              img.alt,
              img.width || undefined,
              img.height || undefined
            )
          }
        },
        priority: 0
      })
    }
  }

  constructor(src: string, alt: string, width?: number, height?: number, key?: NodeKey) {
    super(key)
    this.__src = src
    this.__alt = alt
    this.__width = width
    this.__height = height
  }

  override exportJSON(): SerializedImageNode {
    return {
      type: "image",
      version: 1,
      src: this.__src,
      alt: this.__alt,
      width: this.__width,
      height: this.__height
    }
  }

  override exportDOM(): DOMExportOutput {
    const img = document.createElement("img")
    img.src = this.__src
    img.alt = this.__alt
    if (this.__width) img.width = this.__width
    if (this.__height) img.height = this.__height
    return { element: img }
  }

  override createDOM(_config: EditorConfig): HTMLElement {
    const span = document.createElement("span")
    span.style.display = "inline-block"
    return span
  }

  override updateDOM(): boolean {
    return false
  }

  getSrc(): string {
    return this.__src
  }
  getAlt(): string {
    return this.__alt
  }

  override decorate(): JSX.Element {
    return (
      <Suspense fallback={null}>
        <ImageComponent
          src={this.__src}
          alt={this.__alt}
          width={this.__width}
          height={this.__height}
          nodeKey={this.__key}
        />
      </Suspense>
    )
  }
}

export function $createImageNode(
  src: string,
  alt = "",
  width?: number,
  height?: number
): ImageNode {
  return $applyNodeReplacement(new ImageNode(src, alt, width, height))
}

export function $isImageNode(node: LexicalNode | null | undefined): node is ImageNode {
  return node instanceof ImageNode
}
