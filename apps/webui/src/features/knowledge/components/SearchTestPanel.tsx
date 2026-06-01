/**
 * 检索测试面板——输入查询 + 调参 + 结果展示
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Search, SlidersHorizontal } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
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
import { knowledgeApi } from "@/lib/api/rest/knowledge/knowledge"
import type { SearchResponse } from "@/lib/types/knowledge"

interface SearchTestPanelProps {
  knowledgeBaseId: string
}

export function SearchTestPanel({ knowledgeBaseId }: SearchTestPanelProps) {
  const [query, setQuery] = useState("")
  const [topK, setTopK] = useState(5)
  const [threshold, setThreshold] = useState(0.7)
  const [mode, setMode] = useState("hybrid")
  const [showParams, setShowParams] = useState(false)

  const {
    mutate: search,
    data,
    isPending
  } = useMutation({
    mutationFn: () => knowledgeApi.search(knowledgeBaseId, { query, topK, threshold, mode })
  })

  function handleSearch() {
    if (!query.trim()) return
    search()
  }

  return (
    <div className="space-y-4">
      {/* 搜索栏 */}
      <div className="flex gap-2">
        <Input
          placeholder="输入检索问题..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          className="flex-1"
        />
        <Button onClick={handleSearch} disabled={isPending || !query.trim()}>
          <Search className="mr-1 size-4" />
          {isPending ? "检索中..." : "检索"}
        </Button>
        <Button variant="outline" size="icon" onClick={() => setShowParams(!showParams)}>
          <SlidersHorizontal className="size-4" />
        </Button>
      </div>

      {/* 调参面板 */}
      {showParams && (
        <Card>
          <CardContent className="grid gap-4 pt-4 sm:grid-cols-3">
            <div className="space-y-1">
              <Label>Top-K</Label>
              <Input
                type="number"
                min={1}
                max={20}
                value={topK}
                onChange={(e) => setTopK(Number(e.target.value))}
              />
            </div>
            <div className="space-y-1">
              <Label>相似度阈值</Label>
              <Input
                type="number"
                min={0}
                max={1}
                step={0.05}
                value={threshold}
                onChange={(e) => setThreshold(Number(e.target.value))}
              />
            </div>
            <div className="space-y-1">
              <Label>检索模式</Label>
              <Select value={mode} onValueChange={(v) => setMode(v ?? "hybrid")}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="vector">向量检索</SelectItem>
                  <SelectItem value="keyword">关键词检索</SelectItem>
                  <SelectItem value="hybrid">混合检索</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>
      )}

      {/* 结果展示 */}
      {data && <SearchResults data={data} />}
    </div>
  )
}

function SearchResults({ data }: { data: SearchResponse }) {
  return (
    <div className="space-y-4">
      {/* 生成答案 */}
      {data.answer && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">生成答案</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="prose prose-sm dark:prose-invert max-w-none whitespace-pre-wrap">
              {data.answer}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 检索结果列表 */}
      <div className="space-y-2">
        <h3 className="font-medium text-sm">检索结果（{data.results.length} 条）</h3>
        {data.results.map((item) => (
          <Card key={item.id}>
            <CardContent className="pt-4">
              <div className="mb-2 flex items-center gap-2">
                <Badge variant="secondary">{item.source}</Badge>
                <span className="text-muted-foreground text-xs">
                  相似度: {(item.score * 100).toFixed(1)}%
                </span>
              </div>
              <p className="text-sm leading-relaxed">{item.content}</p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
