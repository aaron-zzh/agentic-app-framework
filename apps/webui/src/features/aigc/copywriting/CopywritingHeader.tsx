/**
 * 文案面板头部：类型切换（口播/小红书/爆款复制）+ 生成图像/保存文档动作
 * @author AaronZZH & Kiro
 */

import { Image, Sparkles } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAigcStore } from "../store"

interface CopywritingHeaderProps {
  /** 是否展示文档关联动作（viral 仅在结果步展示） */
  showDocActions: boolean
  /** 文案生成或改写进行中 */
  generating: boolean
  /** Agent 保存并由事件返回的数据库草稿 ID */
  documentId: number | null
}

export function CopywritingHeader({
  showDocActions,
  generating,
  documentId
}: CopywritingHeaderProps) {
  const type = useAigcStore((s) => s.copywritingType)
  const setType = useAigcStore((s) => s.setCopywritingType)
  const content = useAigcStore((s) => s.copywritingContent)
  const setOpen = useAigcStore((s) => s.setCopywritingPanelOpen)

  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <Label className="shrink-0 text-muted-foreground text-xs">类型</Label>
        <Tabs value={type} onValueChange={(v) => setType(v as "voiceover" | "redbook" | "viral")}>
          <TabsList className="h-7">
            <TabsTrigger value="voiceover" className="h-6 px-3 text-xs">
              口播
            </TabsTrigger>
            <TabsTrigger value="redbook" className="h-6 px-3 text-xs">
              小红书
            </TabsTrigger>
            <TabsTrigger value="viral" className="h-6 px-3 text-xs">
              <Sparkles className="mr-1 size-3" />
              爆款复制
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
      {showDocActions && (
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="xs"
            className="gap-1"
            title="将当前内容发送到图像生成"
            disabled={generating || !content.trim()}
            onClick={() => {
              useAigcStore.getState().setPrompt(content.trim())
              useAigcStore.getState().setGenerationPanelOpen(true)
              setOpen(false)
            }}
          >
            <Image className="size-3" />
            生成图像
          </Button>
          {documentId !== null ? <Badge variant="outline">数据库草稿 #{documentId}</Badge> : null}
        </div>
      )}
    </div>
  )
}
