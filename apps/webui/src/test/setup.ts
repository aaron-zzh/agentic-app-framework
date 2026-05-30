import * as matchers from "@testing-library/jest-dom/matchers"
import { expect } from "vitest"

expect.extend(matchers)

// jsdom 不提供 PointerEvent，@base-ui/react 等库需要它
if (typeof globalThis.PointerEvent === "undefined") {
  class PointerEvent extends MouseEvent {
    readonly pointerId: number
    readonly pointerType: string
    constructor(type: string, params: PointerEventInit = {}) {
      super(type, params)
      this.pointerId = params.pointerId ?? 0
      this.pointerType = params.pointerType ?? ""
    }
  }
  globalThis.PointerEvent = PointerEvent as unknown as typeof globalThis.PointerEvent
}
