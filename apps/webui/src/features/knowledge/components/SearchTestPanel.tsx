/**
 * 检索测试面板——输入查询、调整检索参数并展示原始结果
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Search, SlidersHorizontal } from "lucide-react"
import { useId, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { knowledgeApi } from "@/lib/api/rest/knowledge/knowledge"
import type { KnowledgeSearchMode, SearchResponse, SearchResultItem } from "@/lib/types/knowledge"

interface SearchTestPanelProps {
  knowledgeBaseId: string
}

export function SearchTestPanel({ knowledgeBaseId }: SearchTestPanelProps) {
  const queryId = useId()
  const topKId = useId()
  const thresholdId = useId()
  const modeId = useId()
  const [query, setQuery] = useState("")
  const [topK, setTopK] = useState(5)
  const [threshold, setThreshold] = useState(0.7)
  const [mode, setMode] = useState<KnowledgeSearchMode>("hybrid")
  const [showParams, setShowParams] = useState(false)
  const vectorThresholdEnabled = mode === "vector" || mode === "hybrid"

  const {
    mutate: search,
    data,
    isPending
  } = useMutation({
    mutationFn: () =>
      knowledgeApi.search({
        query: query.trim(),
        knowledgeBaseIds: [knowledgeBaseId],
        topK,
        threshold: vectorThresholdEnabled ? threshold : 0,
        mode
      })
  })

  function handleSearch() {
    if (!query.trim()) return
    search()
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-2">
        <Label htmlFor={queryId} className="sr-only">
          检索内容
        </Label>
        <Input
          id={queryId}
          placeholder="输入检索问题..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") handleSearch()
          }}
          className="flex-1"
        />
        <Button onClick={handleSearch} disabled={isPending || !query.trim()}>
          <Search data-icon="inline-start" />
          {isPending ? "检索中..." : "检索"}
        </Button>
        <Button
          variant="outline"
          size="icon"
          onClick={() => setShowParams((visible) => !visible)}
          aria-label="切换检索参数"
        >
          <SlidersHorizontal />
        </Button>
      </div>

      {showParams ? (
        <Card>
          <CardContent className="grid gap-4 pt-4 sm:grid-cols-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor={topKId}>Top-K</Label>
              <Input
                id={topKId}
                type="number"
                min={1}
                max={20}
                value={topK}
                onChange={(event) => setTopK(Math.min(20, Math.max(1, Number(event.target.value))))}
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor={thresholdId}>相似度阈值</Label>
              <Input
                id={thresholdId}
                type="number"
                min={0}
                max={1}
                step={0.05}
                value={threshold}
                disabled={!vectorThresholdEnabled}
                onChange={(event) =>
                  setThreshold(Math.min(1, Math.max(0, Number(event.target.value))))
                }
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor={modeId}>检索模式</Label>
              <Select
                value={mode}
                onValueChange={(value) => {
                  if (isKnowledgeSearchMode(value)) setMode(value)
                }}
              >
                <SelectTrigger id={modeId}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="vector">向量检索</SelectItem>
                    <SelectItem value="keyword">关键词检索</SelectItem>
                    <SelectItem value="graph">图检索</SelectItem>
                    <SelectItem value="hybrid">混合检索</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>
      ) : null}

      {data ? <SearchResults data={data} /> : null}
    </div>
  )
}

function SearchResults({ data }: { data: SearchResponse }) {
  if (data.results.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyTitle>未检索到结果</EmptyTitle>
          <EmptyDescription>请调整关键词或检索参数后重试</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-medium text-sm">检索结果（{data.results.length} 条）</h3>
      {data.results.map((item) => (
        <SearchResultCard key={searchResultKey(item)} item={item} />
      ))}
    </div>
  )
}

function SearchResultCard({ item }: { item: SearchResultItem }) {
  const sourceLabel = `${item.source.knowledgeBaseName} · ${item.source.sourceType}`
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">{sourceLabel}</Badge>
          {item.matchedChannels.map((channel) => (
            <Badge key={channel} variant="outline">
              {channel}
            </Badge>
          ))}
          <span className="text-muted-foreground text-xs">融合分数：{item.score.toFixed(4)}</span>
        </div>
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{item.content}</p>
        <details>
          <summary className="cursor-pointer text-muted-foreground text-xs">来源与证据</summary>
          <pre className="mt-2 overflow-x-auto rounded-md bg-muted p-3 text-xs">
            {JSON.stringify(item.source, null, 2)}
          </pre>
        </details>
      </CardContent>
    </Card>
  )
}

function isKnowledgeSearchMode(value: string | null): value is KnowledgeSearchMode {
  return value === "vector" || value === "keyword" || value === "graph" || value === "hybrid"
}

function searchResultKey(item: SearchResultItem): string {
  return item.candidateKey
}
