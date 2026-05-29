/**
 * 协作者列表——显示当前在线用户头像
 * @author AaronZZH & Kiro
 */

"use client"

import type { Collaborator } from "./use-canvas-collaboration"

interface CanvasCollaboratorsProps {
  collaborators: Collaborator[]
}

/** 画板右上角协作者头像列表 */
export function CanvasCollaborators({ collaborators }: CanvasCollaboratorsProps) {
  if (collaborators.length === 0) return null

  return (
    <div className="absolute top-2 right-2 z-50 flex items-center gap-1">
      {collaborators.map((user) => (
        <div
          key={user.id}
          className="flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-medium text-white"
          style={{ backgroundColor: user.color, borderColor: user.color }}
          title={user.name}
        >
          {user.avatar ? (
            // biome-ignore lint/performance/noImgElement: 头像为动态 URL
            <img src={user.avatar} alt={user.name} className="h-full w-full rounded-full" />
          ) : (
            user.name.charAt(0).toUpperCase()
          )}
        </div>
      ))}
      <span className="ml-1 text-xs text-muted-foreground">
        {collaborators.length} 人在线
      </span>
    </div>
  )
}
