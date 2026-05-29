/**
 * 知识库设置——分块策略、Embedding 模型配置
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
import { useUpdateKnowledgeBase } from "@/lib/queries/use-knowledge"
import type { KnowledgeBase } from "@/lib/types/knowledge"

const CHUNK_STRATEGIES = [
  { value: "fixed", label: "固定长度" },
  { value: "recursive", label: "递归字符" },
  { value: "semantic", label: "语义边界" }
] as const

const EMBEDDING_MODELS = [
  { value: "text-embedding-3-small", label: "text-embedding-3-small" },
  { value: "text-embedding-3-large", label: "text-embedding-3-large" },
  { value: "bge-large-zh", label: "BGE Large (中文)" },
  { value: "m3e-base", label: "M3E Base" }
] as const

interface KnowledgeSettingsProps {
  knowledgeBase: KnowledgeBase
}

export function KnowledgeSettings({ knowledgeBase }: KnowledgeSettingsProps) {
  const [strategy, setStrategy] = useState(knowledgeBase.chunkStrategy)
  const [chunkSize, setChunkSize] = useState(knowledgeBase.chunkSize)
  const [chunkOverlap, setChunkOverlap] = useState(knowledgeBase.chunkOverlap)
  const [model, setModel] = useState(knowledgeBase.embeddingModel)

  const { mutate: update, isPending } = useUpdateKnowledgeBase()

  function handleSave() {
    update({
      id: knowledgeBase.id,
      data: { chunkStrategy: strategy, chunkSize, chunkOverlap, embeddingModel: model }
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
            onValueChange={(v) => v && setStrategy(v as KnowledgeBase["chunkStrategy"])}
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
              onChange={(e) => setChunkOverlap(Number(e.target.value))}
            />
          </div>
        </div>

        {/* Embedding 模型 */}
        <div className="space-y-1">
          <Label>Embedding 模型</Label>
          <Select value={model} onValueChange={(v) => setModel(v ?? "")}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {EMBEDDING_MODELS.map((m) => (
                <SelectItem key={m.value} value={m.value}>
                  {m.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? "保存中..." : "保存设置"}
        </Button>
      </CardContent>
    </Card>
  )
}
