/**
 * 画板 AI 辅助面板——提供 AI 生成图表、整理布局、内容建议、手绘转换
 * @author AaronZZH & Kiro
 */

"use client"

import type { Editor } from "@tldraw/tldraw"
import { LayoutGrid, Lightbulb, PenTool, Sparkles } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Textarea } from "@/components/ui/textarea"
import type { EntityDef } from "@/lib/types/entity"

interface CanvasAIPanelProps {
  editor: Editor
  entity: EntityDef
}

/** AI 辅助操作类型 */
type AIAction = "generate" | "layout" | "suggest" | "convert"

/** 画板 AI 辅助面板 */
// biome-ignore lint/correctness/noUnusedFunctionParameters: entity 在函数体中使用
export function CanvasAIPanel({ editor, entity }: CanvasAIPanelProps) {
  const [prompt, setPrompt] = useState("")
  const [loading, setLoading] = useState(false)
  const [_activeAction, _setActiveAction] = useState<AIAction | null>(null)

  /** AI 生成图表（描述→流程图/思维导图） */
  const handleGenerate = async () => {
    if (!prompt.trim()) return
    setLoading(true)
    try {
      // TODO: 调用后端 AI 接口生成图表数据
      // POST /api/{entity.slug}/canvas/ai/generate
      // body: { prompt, type: 'flowchart' | 'mindmap' }
      // 返回 tldraw shapes 数组，插入到画布
    } finally {
      setLoading(false)
    }
  }

  /** AI 整理布局（选中元素→自动排列对齐） */
  const handleAutoLayout = async () => {
    const selectedShapes = editor.getSelectedShapes()
    if (selectedShapes.length === 0) return
    setLoading(true)
    try {
      // TODO: 调用后端 AI 接口计算最优布局
      // POST /api/{entity.slug}/canvas/ai/layout
      // body: { shapes: selectedShapes }
      // 返回新的位置坐标，批量更新
    } finally {
      setLoading(false)
    }
  }

  /** AI 内容建议（根据画板内容建议补充节点） */
  const handleSuggest = async () => {
    const _allShapes = editor.getCurrentPageShapes()
    setLoading(true)
    try {
      // TODO: 调用后端 AI 接口获取建议
      // POST /api/{entity.slug}/canvas/ai/suggest
      // body: { shapes: allShapes, entityContext: entity.slug }
      // 返回建议节点列表
    } finally {
      setLoading(false)
    }
  }

  /** 手绘→规范图转换 */
  const handleConvert = async () => {
    const selectedShapes = editor.getSelectedShapes()
    if (selectedShapes.length === 0) return
    setLoading(true)
    try {
      // TODO: 调用后端 AI 接口转换手绘为规范图形
      // POST /api/{entity.slug}/canvas/ai/convert
      // body: { shapes: selectedShapes }
      // 返回规范化的 shapes，替换原有手绘
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="absolute bottom-4 left-1/2 z-50 -translate-x-1/2">
      <div className="flex items-center gap-1 rounded-lg border bg-background/95 p-1 shadow-lg backdrop-blur">
        {/* AI 生成图表 */}
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="sm" title="AI 生成图表" disabled={loading}>
              <Sparkles className="h-4 w-4" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-72" side="top">
            <div className="space-y-2">
              <p className="font-medium text-sm">AI 生成图表</p>
              <Textarea
                placeholder="描述你想生成的图表，如：用户注册流程图..."
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                rows={3}
              />
              <Button size="sm" onClick={handleGenerate} disabled={loading || !prompt.trim()}>
                生成
              </Button>
            </div>
          </PopoverContent>
        </Popover>

        {/* AI 整理布局 */}
        <Button
          variant="ghost"
          size="sm"
          title="AI 整理布局（选中元素后使用）"
          onClick={handleAutoLayout}
          disabled={loading}
        >
          <LayoutGrid className="h-4 w-4" />
        </Button>

        {/* AI 内容建议 */}
        <Button
          variant="ghost"
          size="sm"
          title="AI 内容建议"
          onClick={handleSuggest}
          disabled={loading}
        >
          <Lightbulb className="h-4 w-4" />
        </Button>

        {/* 手绘→规范图转换 */}
        <Button
          variant="ghost"
          size="sm"
          title="手绘转规范图（选中手绘元素后使用）"
          onClick={handleConvert}
          disabled={loading}
        >
          <PenTool className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
