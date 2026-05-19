/**
 * 在线编辑者头像组：显示当前记录的其他在线用户
 * @author AaronZZH & Kiro
 */

"use client"

import { Avatar, AvatarFallback, AvatarGroup, AvatarImage } from "@/components/ui/avatar"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import type { PresenceUser } from "@/lib/hooks/use-record-presence"

interface PresenceAvatarsProps {
  viewers: PresenceUser[]
}

/** 取用户名首字作为头像 fallback */
function getInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}

/**
 * 表单顶部在线编辑者头像堆叠组件
 */
export function PresenceAvatars({ viewers }: PresenceAvatarsProps) {
  if (viewers.length === 0) return null

  return (
    <TooltipProvider>
      <AvatarGroup>
        {viewers.map((user) => (
          <Tooltip key={user.id}>
            <TooltipTrigger>
              <Avatar size="sm">
                {user.avatar && <AvatarImage src={user.avatar} alt={user.name} />}
                <AvatarFallback>{getInitial(user.name)}</AvatarFallback>
              </Avatar>
            </TooltipTrigger>
            <TooltipContent>{user.name} 正在编辑</TooltipContent>
          </Tooltip>
        ))}
      </AvatarGroup>
    </TooltipProvider>
  )
}
