/**
 * 项目画布——tldraw 无限画布 + 项目级持久化
 *
 * 复用 CanvasPanel，persistenceKey=project-canvas-{projectId}
 *
 * @author AaronZZH & Kiro
 */

import { CanvasPanel } from "./CanvasPanel"

interface ProjectCanvasProps {
  projectId: number
}

export function ProjectCanvas({ projectId }: ProjectCanvasProps) {
  return <CanvasPanel persistenceKey={`project-canvas-${projectId}`} />
}
