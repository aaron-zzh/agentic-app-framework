/**
 * 风格快捷调整弹窗——从 MediaPreviewCard 触发，调整风格后重新生成
 * @author AaronZZH & Kiro
 */

"use client"

import { Paintbrush } from "lucide-react"
import { useId, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils/index"

interface StyleAdjustDialogProps {
  initialPrompt?: string
  trigger?: React.ReactNode
}

const STYLE_OPTIONS = [
  { value: "realistic", label: "写实" },
  { value: "anime", label: "动漫" },
  { value: "watercolor", label: "水彩" },
  { value: "pixel", label: "像素" }
] as const

export function StyleAdjustDialog({ initialPrompt = "", trigger }: StyleAdjustDialogProps) {
  const uid = useId()
  const [open, setOpen] = useState(false)
  const [style, setStyle] = useState("realistic")
  const [prompt, setPrompt] = useState(initialPrompt)
  const generateImage = useGenerateImage()

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) {
      setPrompt(initialPrompt)
    }
    setOpen(nextOpen)
  }

  function handleConfirm() {
    const finalPrompt =
      style !== "realistic"
        ? `${prompt}, ${STYLE_OPTIONS.find((s) => s.value === style)?.label}风格`
        : prompt
    generateImage.mutate({ prompt: finalPrompt })
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        render={
          trigger ? (
            <span>{trigger}</span>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              className="h-6 px-2 text-muted-foreground text-xs hover:text-foreground"
            >
              <Paintbrush className="mr-1 size-3" />
              调整风格
            </Button>
          )
        }
      />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>调整风格</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-4 py-2">
          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">风格</span>
            <ToggleGroup
              value={[style]}
              onValueChange={(v) => {
                if (v.length > 0) setStyle(v[v.length - 1])
              }}
              className="justify-start"
            >
              {STYLE_OPTIONS.map((opt) => (
                <ToggleGroupItem
                  key={opt.value}
                  value={opt.value}
                  className={cn("text-xs", style === opt.value && "border-primary")}
                >
                  {opt.label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor={`${uid}-prompt`} className="font-medium text-sm">
              描述
            </label>
            <Textarea
              id={`${uid}-prompt`}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="编辑生成描述..."
              className="min-h-[100px] resize-y"
            />
          </div>
          <Button
            onClick={handleConfirm}
            disabled={generateImage.isPending || !prompt.trim()}
            className="bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
          >
            {generateImage.isPending ? "生成中..." : "重新生成"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
