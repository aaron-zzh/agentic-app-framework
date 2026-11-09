/**
 * 知识库设置——分块策略配置，展示创建时记录的 Embedding 模型（只读）
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { useUpdateKnowledgeBase } from "@/lib/api/rest/knowledge/knowledge"
import type { KnowledgeBase, KnowledgeChunkStrategy } from "@/lib/types/knowledge"

const CHUNK_STRATEGIES = [
  { value: "auto", label: "自动（默认 800 字）" },
  { value: "semantic", label: "自定义大小" }
] as const satisfies readonly { value: KnowledgeChunkStrategy; label: string }[]

interface KnowledgeSettingsProps {
  knowledgeBase: KnowledgeBase
}

export function KnowledgeSettings({ knowledgeBase }: KnowledgeSettingsProps) {
  const [strategy, setStrategy] = useState(knowledgeBase.chunkStrategy)
  const [chunkSize, setChunkSize] = useState(knowledgeBase.chunkSize)
  const [chunkOverlap, setChunkOverlap] = useState(knowledgeBase.chunkOverlap)

  const { mutate: update, isPending } = useUpdateKnowledgeBase()
  const isAuto = strategy === "auto"

  function handleSave() {
    update({
      id: knowledgeBase.id,
      data: { chunkStrategy: strategy, chunkSize, chunkOverlap }
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">分块与向量化设置</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* 分块策略 */}
        <div className="space-y-1">
          <Label>分块策略</Label>
          <Select
            value={strategy}
            onValueChange={(v) => v && setStrategy(v as KnowledgeChunkStrategy)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CHUNK_STRATEGIES.map((s) => (
                <SelectItem key={s.value} value={s.value}>
                  {s.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-muted-foreground text-xs">
            先按文档章节拆分，再在章节内按段落与句末标点均衡切分；不同上级章节的内容不会进入同一块，同一上级下的短小节可能合并。
            {isAuto && "自动模式每块 800 字、重叠 50 字，下方块大小与重叠不生效。"}
          </p>
        </div>

        {/* 块大小 + 重叠 */}
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <Label>块大小（字符数）</Label>
            <Input
              type="number"
              min={100}
              max={4000}
              step={100}
              value={chunkSize}
              disabled={isAuto}
              onChange={(e) => setChunkSize(Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label>重叠窗口（字符数）</Label>
            <Input
              type="number"
              min={0}
              max={500}
              step={50}
              value={chunkOverlap}
              disabled={isAuto}
              onChange={(e) => setChunkOverlap(Number(e.target.value))}
            />
          </div>
        </div>

        {/* Embedding 模型：全局唯一，创建时记录，只读 */}
        <div className="space-y-1">
          <Label>Embedding 模型（创建时记录，只读）</Label>
          <Input value={knowledgeBase.embeddingModel ?? "-"} readOnly disabled />
        </div>

        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? "保存中..." : "保存设置"}
        </Button>
      </CardContent>
    </Card>
  )
}
