/**
 * 数字人卡片组件
 * @author Kiro
 */

"use client"

import { Edit, Eye, MoreVertical, Trash } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import type { DigitalHuman } from "../types"

interface DigitalHumanCardProps {
  digitalHuman: DigitalHuman
  onView: (id: string) => void
  onEdit?: (id: string) => void
  onDelete?: (id: string) => void
}

export function DigitalHumanCard({
  digitalHuman,
  onView,
  onEdit,
  onDelete
}: DigitalHumanCardProps) {
  const canEdit = digitalHuman.permissions?.canEdit
  const isOrgShared = !digitalHuman.permissions?.canEdit && digitalHuman.organizationId

  return (
    <Card className="group overflow-hidden shadow-sm transition-shadow hover:shadow-lg">
      {/* 头像 */}
      <div className="relative aspect-square overflow-hidden bg-muted">
        <img
          src={digitalHuman.avatarImageUrl}
          alt={digitalHuman.name}
          loading="lazy"
          className="size-full object-cover transition-transform group-hover:scale-105"
        />
        {isOrgShared && (
          <Badge className="absolute right-2 top-2" variant="secondary">
            组织共享
          </Badge>
        )}
        {digitalHuman.status === "archived" && (
          <Badge className="absolute left-2 top-2" variant="outline">
            已归档
          </Badge>
        )}
      </div>

      {/* 内容 */}
      <div className="p-4">
        <h3 className="mb-1 truncate font-semibold">{digitalHuman.name}</h3>
        <p className="mb-2 text-muted-foreground text-sm">
          {digitalHuman.voiceConfig.voiceName} · {digitalHuman.appearance.gender === "female" ? "女声" : digitalHuman.appearance.gender === "male" ? "男声" : "中性"}
        </p>

        {/* 标签 */}
        {digitalHuman.tags.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-1">
            {digitalHuman.tags.slice(0, 3).map((tag) => (
              <Badge key={tag} variant="outline" className="text-xs">
                {tag}
              </Badge>
            ))}
            {digitalHuman.tags.length > 3 && (
              <Badge variant="outline" className="text-xs">
                +{digitalHuman.tags.length - 3}
              </Badge>
            )}
          </div>
        )}

        {/* 统计 */}
        <div className="mb-3 space-y-1 text-muted-foreground text-xs">
          <div>使用 {digitalHuman.stats.videoCount} 次</div>
          {digitalHuman.assistantId && <div>已关联助理</div>}
        </div>

        {/* 操作 */}
        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => onView(digitalHuman.id)} className="flex-1">
            <Eye className="mr-1 size-3.5" />
            查看
          </Button>

          {canEdit && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button size="sm" variant="ghost">
                  <MoreVertical className="size-3.5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => onEdit?.(digitalHuman.id)}>
                  <Edit className="mr-2 size-3.5" />
                  编辑
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => onDelete?.(digitalHuman.id)}
                  className="text-destructive"
                >
                  <Trash className="mr-2 size-3.5" />
                  删除
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>
    </Card>
  )
}
