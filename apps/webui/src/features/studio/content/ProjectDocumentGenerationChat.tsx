/**
 * 项目 DOCUMENT_VERSION 对象的对话式 AI 生成界面。
 * Assistant 仅流式返回正文，成功后统一通过项目 candidate API 原子持久化。
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, CheckCircle2, Loader2, Send, Sparkles, UserRound } from "lucide-react"
import { useEffect, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import {
  COPYWRITING_ROLE_KEY,
  copywritingExecutionRequest
} from "@/features/aigc/copywriting/use-copywriting"
import { executeAssistantAgUi } from "@/lib/api/assistant-agui"
import { type AiSkillVO, COPYWRITING_SKILL_CATEGORY, useAiSkills } from "@/lib/api/rest/ai"
import type { AigcObjectVersionView, AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useRecordAigcDocumentCandidate } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils"

interface ProjectDocumentGenerationChatProps {
  project: AigcProject
  object: AigcProjectObject
  onCreated: (candidate: AigcObjectVersionView) => void
}

interface GenerationTurn {
  id: string
  prompt: string
  response: string
  status: "streaming" | "saving" | "saved" | "error"
}

const EMPTY_SKILLS: AiSkillVO[] = []

export function ProjectDocumentGenerationChat({
  project,
  object,
  onCreated
}: ProjectDocumentGenerationChatProps) {
  const skillsQuery = useAiSkills({
    categoryCode: COPYWRITING_SKILL_CATEGORY,
    publishedOnly: true,
    roleKey: COPYWRITING_ROLE_KEY
  })
  const skills = skillsQuery.data ?? EMPTY_SKILLS
  const [selectedSkillCode, setSelectedSkillCode] = useState("")
  const [prompt, setPrompt] = useState("")
  const [turns, setTurns] = useState<GenerationTurn[]>([])
  const [generating, setGenerating] = useState(false)
  const recordCandidate = useRecordAigcDocumentCandidate()

  useEffect(() => {
    if (skills.some((skill) => skill.code === selectedSkillCode)) return
    setSelectedSkillCode(skills[0]?.code ?? "")
  }, [selectedSkillCode, skills])

  const selectedSkill = skills.find((skill) => skill.code === selectedSkillCode)

  function updateTurn(id: string, update: Partial<GenerationTurn>) {
    setTurns((current) => current.map((turn) => (turn.id === id ? { ...turn, ...update } : turn)))
  }

  async function generate() {
    const instruction = prompt.trim()
    if (!instruction || !selectedSkill || generating) return

    const turnId = crypto.randomUUID()
    setTurns((current) => [
      ...current,
      { id: turnId, prompt: instruction, response: "", status: "streaming" }
    ])
    setGenerating(true)
    let generated = ""
    let completed = false
    let runError: Error | null = null

    try {
      await executeAssistantAgUi(
        copywritingExecutionRequest({
          text: `请为项目对象「${object.title || object.stableKey}」生成可直接保存的 Markdown 文档正文。只输出正文，不要附加解释。\n\n用户要求：${instruction}`,
          skillCode: selectedSkill.code,
          variables: {
            projectName: project.name,
            projectDescription: project.description,
            projectObjectId: object.id,
            objectType: object.objectType,
            objectTitle: object.title || object.stableKey
          },
          artifactPersistence: "RETURN_ONLY"
        }),
        {
          onChunk: (delta) => {
            generated += delta
            updateTurn(turnId, { response: generated })
          },
          onDone: (event) => {
            if (event.interrupts.length > 0) {
              runError = new Error("AI 生成意外进入等待授权状态")
              return
            }
            completed = true
          },
          onError: (error) => {
            runError = error
          }
        }
      )

      if (runError !== null) throw runError
      if (!completed || !generated.trim()) throw new Error("AI 未返回可保存的文档正文")

      updateTurn(turnId, { response: generated, status: "saving" })
      const candidate = await recordCandidate.mutateAsync({
        projectId: project.id,
        objectId: object.id,
        expectedProjectVersion: project.version,
        expectedGraphRevision: project.graphRevision,
        title: object.title || object.stableKey,
        content: generated,
        format: "MARKDOWN",
        summary: `AI 技能「${selectedSkill.name}」生成`,
        idempotencyKey: crypto.randomUUID()
      })
      updateTurn(turnId, { status: "saved" })
      setPrompt("")
      toast.success("AI 文档已保存为项目候选")
      onCreated(candidate)
    } catch (error) {
      updateTurn(turnId, { status: "error" })
      toast.error(error instanceof Error ? error.message : "AI 文档生成失败")
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div className="grid min-h-[560px] overflow-hidden rounded-xl border lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside className="flex min-h-0 flex-col border-b bg-muted/20 lg:border-r lg:border-b-0">
        <div className="border-b px-4 py-3">
          <p className="font-medium text-sm">选择文案技能</p>
          <p className="mt-1 text-muted-foreground text-xs">
            仅显示内容创作者角色下已发布的文案技能。
          </p>
        </div>
        <ScrollArea className="max-h-48 flex-1 lg:max-h-none">
          <div className="flex flex-col gap-2 p-3">
            {skillsQuery.isLoading ? (
              ["one", "two", "three"].map((key) => <Skeleton key={key} className="h-16" />)
            ) : skills.length === 0 ? (
              <Empty className="min-h-44 border-0">
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <Sparkles />
                  </EmptyMedia>
                  <EmptyTitle>暂无文案技能</EmptyTitle>
                  <EmptyDescription>请先发布并绑定文案分类技能。</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : (
              skills.map((skill) => (
                <button
                  key={skill.id}
                  type="button"
                  className={cn(
                    "rounded-lg border p-3 text-left transition-colors",
                    selectedSkillCode === skill.code
                      ? "border-primary/50 bg-primary/10"
                      : "bg-background hover:bg-muted/60"
                  )}
                  disabled={generating}
                  onClick={() => setSelectedSkillCode(skill.code)}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="truncate font-medium text-sm">{skill.name}</span>
                    {skill.builtIn ? <Badge variant="secondary">内置</Badge> : null}
                  </span>
                  {skill.summary ? (
                    <span className="mt-1 line-clamp-2 block text-muted-foreground text-xs">
                      {skill.summary}
                    </span>
                  ) : null}
                </button>
              ))
            )}
          </div>
        </ScrollArea>
      </aside>

      <section className="flex min-h-0 flex-col bg-background">
        <div className="flex items-center gap-2 border-b px-4 py-3">
          <Bot className="text-primary" />
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium text-sm">
              {selectedSkill ? selectedSkill.name : "项目文档助手"}
            </p>
            <p className="truncate text-muted-foreground text-xs">
              生成成功后保存为新 candidate，不会自动采用
            </p>
          </div>
        </div>

        <ScrollArea className="min-h-0 flex-1">
          <div className="flex min-h-80 flex-col gap-4 p-4">
            {turns.length === 0 ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-2 py-20 text-center text-muted-foreground">
                <Sparkles className="size-8" />
                <p className="font-medium text-sm">描述希望生成的文档</p>
                <p className="max-w-md text-xs">
                  可以说明目标读者、渠道、结构、语气和长度。正文只在生成完成后保存为项目候选。
                </p>
              </div>
            ) : (
              turns.map((turn) => (
                <div key={turn.id} className="flex flex-col gap-3">
                  <div className="ml-auto flex max-w-[85%] items-start gap-2">
                    <div className="rounded-2xl rounded-tr-sm bg-primary px-4 py-2 text-primary-foreground text-sm">
                      {turn.prompt}
                    </div>
                    <UserRound className="mt-1 shrink-0" />
                  </div>
                  <div className="flex max-w-[92%] items-start gap-2">
                    <Bot className="mt-1 shrink-0 text-primary" />
                    <div className="min-w-0 flex-1 rounded-2xl rounded-tl-sm border bg-muted/30 px-4 py-3">
                      {turn.response ? (
                        <article className="prose prose-sm dark:prose-invert max-w-none">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>{turn.response}</ReactMarkdown>
                        </article>
                      ) : (
                        <span className="flex items-center gap-2 text-muted-foreground text-sm">
                          <Loader2 className="animate-spin" /> 正在生成正文…
                        </span>
                      )}
                      <div className="mt-2 flex items-center gap-1 text-muted-foreground text-xs">
                        {turn.status === "streaming" ? (
                          <>
                            <Loader2 className="animate-spin" /> 生成中
                          </>
                        ) : turn.status === "saving" ? (
                          <>
                            <Loader2 className="animate-spin" /> 正在保存候选
                          </>
                        ) : turn.status === "saved" ? (
                          <>
                            <CheckCircle2 className="text-emerald-500" /> 已保存为候选
                          </>
                        ) : (
                          <span className="text-destructive">生成或保存失败</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </ScrollArea>

        <div className="border-t p-3">
          <div className="rounded-xl border bg-background p-2 focus-within:border-foreground/50">
            <Textarea
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="输入文档要求，例如：生成一篇面向新用户的小红书推广文案…"
              className="min-h-20 resize-none border-0 shadow-none focus-visible:ring-0"
              disabled={generating || skills.length === 0}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault()
                  void generate()
                }
              }}
            />
            <div className="flex items-center justify-between gap-2 px-1 pt-2">
              <span className="truncate text-muted-foreground text-xs">
                {selectedSkill ? `技能：${selectedSkill.name}` : "请选择文案技能"}
              </span>
              <Button
                type="button"
                size="sm"
                disabled={generating || !selectedSkill || !prompt.trim()}
                onClick={() => void generate()}
              >
                {generating ? <Loader2 className="animate-spin" /> : <Send />}
                {generating ? "生成中" : "生成并保存候选"}
              </Button>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
