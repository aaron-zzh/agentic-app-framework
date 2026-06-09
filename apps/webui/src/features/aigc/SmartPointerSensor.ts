import { PointerSensor } from "@dnd-kit/core"
import type { PointerEvent } from "react"

/**
 * 自定义 PointerSensor——跳过带 data-no-dnd 属性的元素（如 ResizableHandle）
 */
export class SmartPointerSensor extends PointerSensor {
  static override activators = [
    {
      eventName: "onPointerDown" as const,
      handler: ({ nativeEvent }: PointerEvent): boolean => {
        if ((nativeEvent.target as HTMLElement)?.closest("[data-no-dnd]")) {
          return false
        }
        return true
      }
    }
  ]
}
