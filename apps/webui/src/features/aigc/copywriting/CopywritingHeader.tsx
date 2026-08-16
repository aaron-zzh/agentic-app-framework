/**
 * 文案面板头部：类型切换（口播/小红书/爆款复制）+ 生成图像/保存文档动作
 * @author AaronZZH & Kiro
 */

import { Check, Image, Loader2, Save, Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAigcStore } from "../store"

interface CopywritingHeaderProps {
  /** 是否展示「生成图像 / 保存文档」动作（viral 仅在结果步展示） */
  showDocActions: boolean
  /** 文档是否已保存 */
  saved: boolean
  /** 保存请求进行中 */
  saving: boolean
  /** 文案生成或改写进行中 */
  generating: boolean
  /** 已持久化的文档 ID；为空表示尚未首次保存 */
  documentId: number | null
  /** 保存为文档 */
  onSaveDoc: () => void
}

export function CopywritingHeader({
  showDocActions,
  saved,
  saving,
  generating,
  documentId,
  onSaveDoc
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
            onClick={() => {
              useAigcStore.getState().setPrompt(content.trim())
              useAigcStore.getState().setGenerationPanelOpen(true)
              setOpen(false)
            }}
          >
            <Image className="size-3" />
            生成图像
          </Button>
          <Button
            variant="outline"
            size="xs"
            className="gap-1"
            title={documentId === null ? "保存文案" : "保存文案更新"}
            disabled={generating || saved || saving || !content.trim()}
            onClick={onSaveDoc}
          >
            {saving ? (
              <Loader2 className="size-3 animate-spin" />
            ) : saved ? (
              <Check className="size-3" />
            ) : (
              <Save className="size-3" />
            )}
            {saving
              ? "保存中..."
              : saved
                ? "已保存"
                : documentId === null
                  ? "保存文案"
                  : "保存更新"}
          </Button>
        </div>
      )}
    </div>
  )
}
