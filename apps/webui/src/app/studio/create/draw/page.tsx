/**
 * 无限画布——tldraw 全屏绘图页
 *
 * @author AaronZZH & Kiro
 */

import { CanvasPanel } from "@/features/studio/projects/CanvasPanel"

export default function StudioDrawPage() {
  return (
    <div className="h-full w-full">
      <CanvasPanel persistenceKey="studio-draw" />
    </div>
  )
}
