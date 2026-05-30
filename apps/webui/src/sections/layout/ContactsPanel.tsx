/**
 * ContactsPanel——联系人面板
 * @author AaronZZH & Kiro
 *
 * 从 AppHeader 用户头像触发，展示联系人列表 + 在线状态
 */

"use client"

import type React from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils/cn"

type OnlineStatus = "online" | "offline" | "away" | "busy"

import { formatTimeAgo } from "@/lib/utils/time"

interface Contact {
  id: string
  name: string
  avatar?: string
  status: OnlineStatus
  /** 最近一条消息预览 */
  lastMessage?: string
  /** 最近消息时间戳 */
  lastMessageAt?: string
}

/** mock 数据（TODO: 后端就绪后替换为 useQuery + GET /api/contacts） */
const mockContacts: Contact[] = [
  {
    id: "1",
    name: "张三",
    status: "online",
    lastMessage: "好的，明天见！",
    lastMessageAt: new Date(Date.now() - 2 * 60000).toISOString()
  },
  {
    id: "2",
    name: "李四",
    status: "online",
    lastMessage: "文档已发给你了",
    lastMessageAt: new Date(Date.now() - 5 * 60000).toISOString()
  },
  {
    id: "3",
    name: "王五",
    status: "away",
    lastMessage: "下周再聊",
    lastMessageAt: new Date(Date.now() - 2 * 86400000).toISOString()
  },
  { id: "4", name: "赵六", status: "online" },
  {
    id: "5",
    name: "陈七",
    status: "offline",
    lastMessage: "收到，谢谢",
    lastMessageAt: new Date(Date.now() - 4 * 86400000).toISOString()
  },
  {
    id: "6",
    name: "刘八",
    status: "busy",
    lastMessage: "我在开会",
    lastMessageAt: new Date(Date.now() - 3600000).toISOString()
  },
  { id: "7", name: "周九", status: "offline" }
]

const statusConfig: Record<OnlineStatus, { color: string; label: string }> = {
  online: { color: "bg-green-500", label: "在线" },
  away: { color: "bg-gray-300", label: "离开" },
  busy: { color: "bg-red-500", label: "忙碌" },
  offline: { color: "bg-gray-300", label: "离线" }
}

interface ContactsPanelProps {
  children: React.ReactNode
}

export function ContactsPanel({ children }: ContactsPanelProps) {
  const contacts = mockContacts
  // const onlineCount = contacts.filter((c) => c.status === "online").length

  return (
    <Popover>
      <PopoverTrigger render={children as React.ReactElement} />
      <PopoverContent side="bottom" align="end" sideOffset={8} className="w-80 overflow-hidden p-0">
        {/* 标题 */}
        <div className="px-4 pt-4 pb-2">
          <h3 className="font-bold text-xl">
            联系人 <span className="text-muted-foreground">({contacts.length})</span>
          </h3>
        </div>

        {/* 列表 */}
        <ScrollArea className="h-96">
          <div className="px-2 pb-3">
            {contacts.map((contact) => (
              <button
                key={contact.id}
                type="button"
                className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-accent/50"
              >
                {/* 头像 + 状态 */}
                <div className="relative shrink-0">
                  <Avatar className="size-12 after:hidden">
                    <AvatarImage src={contact.avatar} alt={contact.name} />
                    <AvatarFallback className="bg-muted font-medium text-sm">
                      {contact.name.slice(0, 1)}
                    </AvatarFallback>
                  </Avatar>
                  <span
                    className={cn(
                      "absolute right-0 bottom-0 size-3.5 rounded-full ring-2 ring-background",
                      statusConfig[contact.status].color
                    )}
                    title={statusConfig[contact.status].label}
                  />
                </div>

                {/* 姓名 + 最近消息 */}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate font-medium text-sm">{contact.name}</p>
                    {contact.lastMessageAt && (
                      <span className="shrink-0 text-[10px] text-muted-foreground">
                        {formatTimeAgo(contact.lastMessageAt)}
                      </span>
                    )}
                  </div>
                  {contact.lastMessage && (
                    <p className="truncate text-muted-foreground text-xs">{contact.lastMessage}</p>
                  )}
                </div>
              </button>
            ))}
          </div>
        </ScrollArea>
      </PopoverContent>
    </Popover>
  )
}
