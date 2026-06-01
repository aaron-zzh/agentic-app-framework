"use client"

/**
 * 图像生成/处理示例页——对接后端 Spring AI 图像接口
 * 路由：/dev/examples/image
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { buildApiUrl } from "@/lib/api/config"

interface ImageResult {
  imageUrl: string
  revisedPrompt?: string
}

interface ProcessResult {
  taskId?: string
  status: string
  imageUrl?: string
}

interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export default function ImageExamplePage() {
  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>图像生成与处理</TypographyH1>
        <TypographyMuted>需要后端启用 aaf.examples.image.enabled=true</TypographyMuted>
      </div>

      <Tabs defaultValue="generate">
        <TabsList>
          <TabsTrigger value="generate">文生图</TabsTrigger>
          <TabsTrigger value="process">图像处理</TabsTrigger>
        </TabsList>

        <TabsContent value="generate">
          <GenerateTab />
        </TabsContent>
        <TabsContent value="process">
          <ProcessTab />
        </TabsContent>
      </Tabs>
    </PageContainer>
  )
}

/** 文生图 Tab */
function GenerateTab() {
  const [prompt, setPrompt] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [result, setResult] = useState<ImageResult | null>(null)

  const handleGenerate = useCallback(async () => {
    if (!prompt.trim()) return
    setLoading(true)
    setError("")
    setResult(null)

    try {
      const res = await fetch(buildApiUrl("/examples/image/generate"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt })
      })
      const json = (await res.json()) as ApiResponse<ImageResult>
      if (json.code !== 0) throw new Error(json.message || "生成失败")
      setResult(json.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : "请求失败")
    } finally {
      setLoading(false)
    }
  }, [prompt])

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>文生图</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex gap-2">
          <Input
            placeholder="输入图片描述，如：一只在太空中飘浮的猫"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleGenerate()}
          />
          <Button onClick={handleGenerate} disabled={loading || !prompt.trim()}>
            {loading ? "生成中..." : "生成"}
          </Button>
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        {result && (
          <div className="space-y-2">
            {result.revisedPrompt && (
              <p className="text-muted-foreground text-xs">
                修正后的提示词：{result.revisedPrompt}
              </p>
            )}
            {/* biome-ignore lint/performance/noImgElement: 动态外部 URL，next/image 不支持 */}
            <img
              src={result.imageUrl}
              alt={prompt}
              className="max-h-96 rounded-lg border object-contain"
            />
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** 图像处理 Tab */
function ProcessTab() {
  const [imageUrl, setImageUrl] = useState("")
  const [method, setMethod] = useState("colorEnhance")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [result, setResult] = useState<ProcessResult | null>(null)
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  /** 轮询异步任务状态 */
  const pollTask = useCallback((taskId: string) => {
    pollingRef.current = setInterval(async () => {
      try {
        const res = await fetch(buildApiUrl(`/examples/image/process/${taskId}`))
        const json = (await res.json()) as ApiResponse<ProcessResult>
        if (json.code !== 0) {
          if (pollingRef.current) clearInterval(pollingRef.current)
          setError(json.message || "查询失败")
          setLoading(false)
          return
        }
        if (json.data.status === "completed" || json.data.imageUrl) {
          if (pollingRef.current) clearInterval(pollingRef.current)
          setResult(json.data)
          setLoading(false)
        } else if (json.data.status === "failed") {
          if (pollingRef.current) clearInterval(pollingRef.current)
          setError("处理失败")
          setLoading(false)
        }
      } catch {
        if (pollingRef.current) clearInterval(pollingRef.current)
        setError("轮询请求失败")
        setLoading(false)
      }
    }, 2000)
  }, [])

  const handleProcess = useCallback(async () => {
    if (!imageUrl.trim()) return
    setLoading(true)
    setError("")
    setResult(null)

    try {
      const res = await fetch(buildApiUrl("/examples/image/process"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageUrl, method })
      })
      const json = (await res.json()) as ApiResponse<ProcessResult>
      if (json.code !== 0) throw new Error(json.message || "处理失败")

      if (json.data.taskId && json.data.status !== "completed") {
        // 异步任务，开始轮询
        setResult(json.data)
        pollTask(json.data.taskId)
      } else {
        setResult(json.data)
        setLoading(false)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "请求失败")
      setLoading(false)
    }
  }, [imageUrl, method, pollTask])

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>图像处理</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Input
          placeholder="输入图片 URL"
          value={imageUrl}
          onChange={(e) => setImageUrl(e.target.value)}
        />
        <div className="flex gap-2">
          <Select value={method} onValueChange={(v) => setMethod(v ?? "colorEnhance")}>
            <SelectTrigger className="w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="colorEnhance">色彩增强</SelectItem>
              <SelectItem value="cartoonize">卡通化</SelectItem>
            </SelectContent>
          </Select>
          <Button onClick={handleProcess} disabled={loading || !imageUrl.trim()}>
            {loading ? "处理中..." : "处理"}
          </Button>
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        {result && (
          <div className="space-y-2">
            <Badge variant="secondary">状态：{result.status}</Badge>
            {result.imageUrl && (
              // biome-ignore lint/performance/noImgElement: 动态外部 URL，next/image 不支持
              <img
                src={result.imageUrl}
                alt="处理结果"
                className="max-h-96 rounded-lg border object-contain"
              />
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
