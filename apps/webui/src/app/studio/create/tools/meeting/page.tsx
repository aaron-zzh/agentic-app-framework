/**
 * /studio/create/tools/meeting——会议记录
 * 实时 ASR 录音转写 → 一键摘要 → 保存为文档
 * @author AaronZZH & Kiro
 */

"use client"

import { BookCheck, Edit2, Eye, Loader2, Mic, MicOff, Sparkles } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"
import {
  GlassCard,
  GlassCardBody,
  GlassCardHeader,
  GlassCardTitle,
  GlowButton,
  SectionHaze
} from "@/components/studio"
import { Button } from "@/components/ui/button"
import type { RichTextEditorHandle } from "@/features/rich-text-editor"
import { RichTextEditor } from "@/features/rich-text-editor"
import { buildWsUrl } from "@/lib/api/config"
import { meetingApi } from "@/lib/api/rest/ai/meeting"
import { useCreateDocument } from "@/lib/queries/use-documents"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"

type WsStatus = "disconnected" | "connecting" | "connected"

export default function StudioToolsMeetingPage() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [lines, setLines] = useState<string[]>([])
  const [summary, setSummary] = useState("")
  const [summarizing, setSummarizing] = useState(false)
  const { mutate: createDoc, isPending: saving } = useCreateDocument()
  const [editMode, setEditMode] = useState(false)
  const editorRef = useRef<RichTextEditorHandle>(null)

  const [recordingDate, setRecordingDate] = useState<string>("")

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioCtxRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)

  const transcript = lines.join("\n")

  const handleStart = useCallback(async () => {
    setLines([])
    setSummary("")
    setRecordingDate(new Date().toISOString().slice(0, 10))

    const params = new URLSearchParams({ lang: "zh-CN", token: accessToken ?? "" })
    const ws = new WebSocket(buildWsUrl(`/ws/asr?${params.toString()}`))
    ws.binaryType = "arraybuffer"
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = async () => {
      setStatus("connected")
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { sampleRate: 16000, channelCount: 1 }
        })
        streamRef.current = stream
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioCtxRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor
        processor.onaudioprocess = (e: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN) return
          ws.send(float32ToPcm16(e.inputBuffer.getChannelData(0)).buffer as ArrayBuffer)
        }
        source.connect(processor)
        processor.connect(audioCtx.destination)
      } catch {
        ws.close()
        toast.error("无法获取麦克风权限")
      }
    }

    ws.onmessage = (event: MessageEvent) => {
      const msg = JSON.parse(event.data as string) as { text: string; final: boolean }
      if (msg.final) {
        setLines((prev) => [...prev, msg.text])
      } else {
        setLines((prev) => {
          const next = [...prev]
          const last = next[next.length - 1]
          if (last && !last.endsWith("。") && !last.endsWith(".")) {
            next[next.length - 1] = msg.text
          } else {
            next.push(msg.text)
          }
          return next
        })
      }
    }

    ws.onclose = () => setStatus("disconnected")
    ws.onerror = () => ws.close()
  }, [accessToken])

  const handleStop = useCallback(() => {
    processorRef.current?.disconnect()
    if (processorRef.current) processorRef.current.onaudioprocess = null
    processorRef.current = null
    audioCtxRef.current?.close()
    audioCtxRef.current = null
    for (const t of streamRef.current?.getTracks() ?? []) t.stop()
    streamRef.current = null
    wsRef.current?.close()
    wsRef.current = null
  }, [])

  const handleSummarize = async () => {
    if (!transcript) return
    setSummarizing(true)
    try {
      const res = await meetingApi.organize({
        transcript,
        meetingDate: recordingDate || new Date().toISOString().slice(0, 10)
      })
      setSummary(res.content ?? "")
    } catch {
      toast.error("整理失败，请重试")
    } finally {
      setSummarizing(false)
    }
  }

  const handleSave = () => {
    const content = editMode ? (editorRef.current?.getContent("markdown") ?? summary) : summary
    if (!content) return
    const date = new Date().toISOString().slice(0, 10)
    const snippet = content.replace(/\s+/g, "").slice(0, 10)
    const title = `${date} ${snippet}`
    createDoc(
      { title, filePath: "", docType: "meeting", content },
      {
        onSuccess: () => toast.success("已保存为文档"),
        onError: () => toast.error("保存失败")
      }
    )
  }

  const recording = status === "connected"

  return (
    <div className="relative mx-auto max-w-4xl p-6">
      <SectionHaze variant="violet" />
      <div className="relative space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Mic className="size-5 text-rose-400" />
            <h1 className="font-semibold text-xl">会议记录</h1>
          </div>
          <div className="flex items-center gap-2">
            {transcript && (
              <>
                <GlowButton tone="ghost" size="sm" disabled={summarizing} onClick={handleSummarize}>
                  {summarizing ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Sparkles className="size-4" />
                  )}
                  {summarizing ? "整理中..." : "一键整理"}
                </GlowButton>
                <GlowButton tone="ghost" size="sm" disabled={saving} onClick={handleSave}>
                  {saving ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <BookCheck className="size-4" />
                  )}
                  {saving ? "保存中..." : "保存文档"}
                </GlowButton>
              </>
            )}
            {status === "disconnected" ? (
              <GlowButton tone="primary" size="sm" onClick={handleStart}>
                <Mic className="size-4" />
                开始录音
              </GlowButton>
            ) : (
              <GlowButton
                tone="ghost"
                size="sm"
                onClick={handleStop}
                disabled={status === "connecting"}
              >
                <MicOff className="size-4" />
                {status === "connecting" ? "连接中..." : "停止录音"}
              </GlowButton>
            )}
          </div>
        </div>

        {/* 实时转写 */}
        <GlassCard glow="none">
          <GlassCardHeader>
            <GlassCardTitle className="flex items-center gap-2">
              转写文字
              {recording && <span className="size-2 animate-pulse rounded-full bg-rose-400" />}
            </GlassCardTitle>
          </GlassCardHeader>
          <GlassCardBody>
            <div className="min-h-48 space-y-1">
              {lines.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  {status === "disconnected"
                    ? "点击【开始录音】后实时显示识别内容..."
                    : "等待识别..."}
                </p>
              ) : (
                lines.map((line, i) => (
                  <p key={`${i}-${line.slice(0, 8)}`} className="text-sm leading-relaxed">
                    {line}
                  </p>
                ))
              )}
            </div>
          </GlassCardBody>
        </GlassCard>

        {/* 整理结果 */}
        {summary && (
          <GlassCard glow="violet">
            <GlassCardHeader>
              <GlassCardTitle>整理结果</GlassCardTitle>
              <Button
                variant="ghost"
                size="sm"
                className="gap-1.5 text-xs"
                onClick={() => setEditMode((v) => !v)}
              >
                {editMode ? <Eye className="size-3.5" /> : <Edit2 className="size-3.5" />}
                {editMode ? "预览" : "编辑"}
              </Button>
            </GlassCardHeader>
            <GlassCardBody>
              {editMode ? (
                <RichTextEditor
                  ref={editorRef}
                  value={summary}
                  mode="markdown"
                  initialValueMode="markdown"
                  minHeight={300}
                  noBorder
                  preset="document"
                  onChange={(v) => setSummary(v)}
                />
              ) : (
                <article className="prose prose-sm dark:prose-invert max-w-none">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{summary}</ReactMarkdown>
                </article>
              )}
            </GlassCardBody>
          </GlassCard>
        )}
      </div>
    </div>
  )
}
