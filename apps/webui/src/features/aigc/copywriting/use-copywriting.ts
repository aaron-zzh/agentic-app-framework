/**
 * 文案面板生成逻辑：口播/小红书生成、改写、爆款复制三步向导（分析→生成）、保存文档
 * 统一收拢生成相关状态与流式回调，面板组件只负责布局
 * @author AaronZZH & Kiro
 */

import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import type { StreamingEditorHandle } from "@/features/rich-text-editor"
import { copywritingApi } from "@/lib/api/rest/ai"
import { useLinkProjectDoc } from "@/lib/queries/use-aigc-projects"
import { useCreateDocument } from "@/lib/queries/use-documents"
import { useAigcStore } from "../store"

/** 文案生成相关状态与动作；参数（type/template/length 等）直接读 store */
export function useCopywriting(projectId?: number) {
  const content = useAigcStore((s) => s.copywritingContent)
  const setContent = useAigcStore((s) => s.setCopywritingContent)
  const type = useAigcStore((s) => s.copywritingType)
  const template = useAigcStore((s) => s.copywritingTemplate)
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const length = useAigcStore((s) => s.copywritingLength)
  const model = useAigcStore((s) => s.copywritingModel)
  const referenceImages = useAigcStore((s) => s.copywritingReferenceImages)

  const [generating, setGenerating] = useState(false)
  const [streamingContent, setStreamingContent] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const streamingEditorRef = useRef<StreamingEditorHandle>(null)

  // 爆款复制向导状态
  const [viralStep, setViralStep] = useState<1 | 2 | 3>(1)
  const [viralSource, setViralSource] = useState("")
  const [viralAnalysis, setViralAnalysis] = useState("")
  const [analyzing, setAnalyzing] = useState(false)
  const analysisEditorRef = useRef<StreamingEditorHandle>(null)
  const resultEditorRef = useRef<StreamingEditorHandle>(null)

  const createDoc = useCreateDocument()
  const linkDoc = useLinkProjectDoc()

  // 切换类型时重置向导、已保存状态、编辑器内容
  useEffect(() => {
    setSaved(false)
    streamingEditorRef.current?.done("")
    if (type !== "viral") {
      setViralStep(1)
      setViralSource("")
      setViralAnalysis("")
    }
  }, [type])

  async function handleSaveDoc() {
    if (!content.trim()) return
    const lines = content.trim().split("\n")
    const title = lines[0].replace(/^#+\s*/, "").trim() || "文案"
    const doc = await createDoc.mutateAsync({
      title,
      content,
      docType: "copywriting",
      filePath: ""
    })
    if (projectId) {
      await linkDoc.mutateAsync({ projectId, docId: doc.id, role: "output" })
    }
    toast.success("已保存为文档")
    setSaved(true)
  }

  async function handleGenerate() {
    setGenerating(true)
    setSaved(false)
    setContent("")
    setStreamingContent("")
    streamingEditorRef.current?.start()
    let acc = ""
    await copywritingApi.generate(
      {
        topic: content || "新品发布",
        type,
        template,
        length,
        translateTo: translateTo || undefined,
        modelId: model || undefined,
        referenceImageKeys:
          referenceImages.length > 0 ? referenceImages.map((img) => img.key) : undefined
      },
      {
        onChunk: (chunk) => {
          acc += chunk
          streamingEditorRef.current?.push(chunk)
        },
        onDone: () => {
          setContent(acc)
          streamingEditorRef.current?.done(acc)
          setStreamingContent(null)
          setGenerating(false)
        },
        onError: (err) => {
          setContent(acc)
          streamingEditorRef.current?.done(acc)
          setStreamingContent(null)
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  async function handleRewrite() {
    if (!content.trim()) return
    const original = content
    setGenerating(true)
    setSaved(false)
    setContent("")
    setStreamingContent("")
    streamingEditorRef.current?.start()
    let acc = ""
    await copywritingApi.rewrite(
      { content: original, modelId: model || undefined },
      {
        onChunk: (chunk) => {
          acc += chunk
          streamingEditorRef.current?.push(chunk)
        },
        onDone: () => {
          setContent(acc)
          streamingEditorRef.current?.done(acc)
          setStreamingContent(null)
          setGenerating(false)
        },
        onError: (err) => {
          setContent(acc)
          streamingEditorRef.current?.done(acc)
          setStreamingContent(null)
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  async function handleAnalyze() {
    if (!viralSource.trim()) return
    setAnalyzing(true)
    setViralAnalysis("")
    setViralStep(2)
    let acc = ""
    await copywritingApi.analyze(
      { content: viralSource, modelId: model || undefined },
      {
        onChunk: (chunk) => {
          acc += chunk
          analysisEditorRef.current?.push(chunk)
        },
        onDone: () => {
          setViralAnalysis(acc)
          analysisEditorRef.current?.done(acc)
          setAnalyzing(false)
        },
        onError: (err) => {
          setViralAnalysis(acc)
          analysisEditorRef.current?.done(acc)
          setAnalyzing(false)
          toast.error(err?.message ?? "分析失败")
        }
      }
    )
  }

  async function handleViralGenerate() {
    setGenerating(true)
    setViralStep(3)
    setContent("")
    let acc = ""
    await copywritingApi.generate(
      {
        topic: "参考爆款结构创作",
        type: "oral",
        template,
        length,
        modelId: model || undefined,
        referenceAnalysis: viralAnalysis
      },
      {
        onChunk: (chunk) => {
          acc += chunk
          resultEditorRef.current?.push(chunk)
        },
        onDone: () => {
          setContent(acc)
          resultEditorRef.current?.done(acc)
          setGenerating(false)
        },
        onError: (err) => {
          setContent(acc)
          resultEditorRef.current?.done(acc)
          setGenerating(false)
          toast.error(err?.message ?? "生成失败")
        }
      }
    )
  }

  return {
    content,
    setContent,
    generating,
    streamingContent,
    saved,
    setSaved,
    streamingEditorRef,
    viralStep,
    setViralStep,
    viralSource,
    setViralSource,
    viralAnalysis,
    setViralAnalysis,
    analyzing,
    analysisEditorRef,
    resultEditorRef,
    createDoc,
    linkDoc,
    handleSaveDoc,
    handleGenerate,
    handleRewrite,
    handleAnalyze,
    handleViralGenerate
  }
}
