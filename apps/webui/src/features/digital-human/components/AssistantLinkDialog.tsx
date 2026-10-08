/**
 * 助理关联对话框
 * @author Kiro
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { useLinkAssistant } from "@/lib/api/rest/digital-human"

interface AssistantLinkDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  digitalHumanId: string
  assistants: Array<{ id: string; name: string }>
}

export function AssistantLinkDialog({
  open,
  onOpenChange,
  digitalHumanId,
  assistants
}: AssistantLinkDialogProps) {
  const linkMutation = useLinkAssistant()
  const [selectedAssistantId, setSelectedAssistantId] = useState<string>("")
  const [syncModelConfig, setSyncModelConfig] = useState(false)

  const handleSubmit = async () => {
    if (!selectedAssistantId) return

    await linkMutation.mutateAsync({
      id: digitalHumanId,
      assistantId: selectedAssistantId,
      syncModelConfig
    })
    onOpenChange(false)
    setSelectedAssistantId("")
    setSyncModelConfig(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>关联助理</DialogTitle>
          <DialogDescription>
            将数字人与助理关联后，视频生成时可自动使用助理的对话能力
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label>选择助理</Label>
            <Select
              value={selectedAssistantId}
              onValueChange={(v) => setSelectedAssistantId(v ?? "")}
            >
              <SelectTrigger>
                <SelectValue placeholder="请选择助理" />
              </SelectTrigger>
              <SelectContent>
                {assistants.map((assistant) => (
                  <SelectItem key={assistant.id} value={assistant.id}>
                    {assistant.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-center space-x-2">
            <Checkbox
              id="sync"
              checked={syncModelConfig}
              onCheckedChange={(checked) => setSyncModelConfig(checked === true)}
            />
            <Label htmlFor="sync" className="font-normal text-sm">
              同步助理的模型配置（温度、最大Token等参数）
            </Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={!selectedAssistantId || linkMutation.isPending}>
            {linkMutation.isPending ? "关联中..." : "确认关联"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
