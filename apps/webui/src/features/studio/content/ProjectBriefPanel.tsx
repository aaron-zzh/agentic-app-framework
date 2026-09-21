/**
 * 项目简报面板：让创建时填写的初始化字段在项目详情页可见、可改，并就地显示 AI 生成状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { useSetState } from "@aaf/hooks"
import { CircleAlert, LoaderCircle, RotateCcw, Save } from "lucide-react"
import { useEffect } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import type {
  AigcProject,
  AigcProjectProgress,
  AigcProjectUpdateInput
} from "@/lib/api/rest/ai/aigc"
import { useUpdateAigcProject } from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"
import {
  PROJECT_INITIALIZATION_FIELDS,
  type ProjectInitializationFieldSpec
} from "./project-initialization-fields"
import { presentGenerationStatus } from "./target-generation-status"

/** 可编辑字段的草稿形态：列表字段以每行一项的文本编辑，提交时再拆回数组。 */
interface BriefDraft extends Record<string, unknown> {
  background: string
  targetAudience: string
  requirements: string
  constraints: string
  keyFacts: string
  keyMessages: string
  styleGuidance: string
  successCriteria: string
  locale: string
  additionalContext: string
}

export interface ProjectBriefPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  project: AigcProject
  readOnly: boolean
  /** 项目级权威进度投影；用于按 itemKey 显示每个字段最近一次生成状态。 */
  progress?: AigcProjectProgress
}

function joinLines(values: string[]): string {
  return values.join("\n")
}

function splitLines(value: string): string[] {
  return value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

function toDraft(project: AigcProject): BriefDraft {
  return {
    background: project.background ?? "",
    targetAudience: project.targetAudience ?? "",
    requirements: joinLines(project.requirements),
    constraints: joinLines(project.constraints),
    keyFacts: joinLines(project.keyFacts),
    keyMessages: joinLines(project.keyMessages),
    styleGuidance: project.styleGuidance ?? "",
    successCriteria: joinLines(project.successCriteria),
    locale: project.locale ?? "",
    additionalContext: project.additionalContext ?? ""
  }
}

/** 空字符串提交为 null，让服务端按"未填写"处理，而不是保存一个空串。 */
function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

/** 取草稿里的文本值；JSON 字段不进草稿，因此缺失时回落空串。 */
function draftValue(draft: BriefDraft, key: string): string {
  const value = draft[key]
  return typeof value === "string" ? value : ""
}

function toUpdateInput(draft: BriefDraft, expectedVersion: number): AigcProjectUpdateInput {
  return {
    background: nullable(draft.background),
    targetAudience: nullable(draft.targetAudience),
    requirements: splitLines(draft.requirements),
    constraints: splitLines(draft.constraints),
    keyFacts: splitLines(draft.keyFacts),
    keyMessages: splitLines(draft.keyMessages),
    styleGuidance: nullable(draft.styleGuidance),
    successCriteria: splitLines(draft.successCriteria),
    locale: nullable(draft.locale),
    additionalContext: nullable(draft.additionalContext),
    expectedVersion
  }
}

export function ProjectBriefPanel({
  open,
  onOpenChange,
  project,
  readOnly,
  progress
}: ProjectBriefPanelProps) {
  const { state: draft, setState: setDraft } = useSetState<BriefDraft>(toDraft(project))
  const update = useUpdateAigcProject()

  // biome-ignore lint/correctness/useExhaustiveDependencies: 打开面板或项目版本推进时以服务端权威值重置草稿，未提交的编辑不跨版本保留
  useEffect(() => {
    if (open) setDraft(toDraft(project))
  }, [open, project.version])

  function save() {
    if (readOnly || update.isPending) return
    update.mutate(
      { id: project.id, data: toUpdateInput(draft, project.version) },
      {
        onSuccess: () => notify.success("项目简报已保存"),
        onError: (error) =>
          notify.error(error instanceof Error ? error.message : "项目简报保存失败")
      }
    )
  }

  function fieldStatus(field: ProjectInitializationFieldSpec) {
    return presentGenerationStatus(progress?.initializationFieldGenerationStatus[field.key])
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-xl">
        <SheetHeader className="border-b">
          <SheetTitle>项目简报</SheetTitle>
          <SheetDescription>
            创建时填写的项目依据，Assistant
            生成内容时以此为准；修改后对后续生成生效，已产出的内容不受影响。
          </SheetDescription>
        </SheetHeader>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 pb-6">
          {readOnly ? (
            <Alert>
              <CircleAlert />
              <AlertTitle>当前只读</AlertTitle>
              <AlertDescription>项目生命周期或权限限制，简报只能查看。</AlertDescription>
            </Alert>
          ) : null}

          {PROJECT_INITIALIZATION_FIELDS.map((field) => {
            const status = fieldStatus(field)
            const inputId = `project-brief-${field.key}`
            if (field.form === "JSON") {
              const entries = Object.entries(project.deliveryRequirements)
              return (
                <section key={field.key} className="flex flex-col gap-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-medium text-sm">{field.label}</h3>
                    {status ? (
                      <Badge variant={status.failed ? "destructive" : "outline"}>
                        {status.label}
                      </Badge>
                    ) : null}
                  </div>
                  {field.hint ? (
                    <p className="text-muted-foreground text-xs">{field.hint}</p>
                  ) : null}
                  {entries.length === 0 ? (
                    <p className="text-muted-foreground text-sm">未设置</p>
                  ) : (
                    <dl className="flex flex-col gap-1 rounded-lg border p-3 text-sm">
                      {entries.map(([key, value]) => (
                        <div key={key} className="flex flex-wrap gap-2">
                          <dt className="text-muted-foreground">{key}</dt>
                          <dd className="min-w-0 break-words">{JSON.stringify(value)}</dd>
                        </div>
                      ))}
                    </dl>
                  )}
                </section>
              )
            }
            return (
              <section key={field.key} className="flex flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <label htmlFor={inputId} className="font-medium text-sm">
                    {field.label}
                  </label>
                  {status ? (
                    <Badge variant={status.failed ? "destructive" : "outline"}>
                      {status.label}
                    </Badge>
                  ) : null}
                </div>
                {field.hint ? <p className="text-muted-foreground text-xs">{field.hint}</p> : null}
                {field.form === "TEXT" ? (
                  <Input
                    id={inputId}
                    value={draftValue(draft, field.key)}
                    disabled={readOnly}
                    onChange={(event) => setDraft({ [field.key]: event.target.value })}
                  />
                ) : (
                  <Textarea
                    id={inputId}
                    value={draftValue(draft, field.key)}
                    disabled={readOnly}
                    className={field.form === "STRING_LIST" ? "min-h-24" : "min-h-20"}
                    onChange={(event) => setDraft({ [field.key]: event.target.value })}
                  />
                )}
              </section>
            )
          })}
        </div>

        {readOnly ? null : (
          <div className="flex items-center justify-end gap-2 border-t px-4 py-3">
            <Button
              type="button"
              variant="ghost"
              disabled={update.isPending}
              onClick={() => setDraft(toDraft(project))}
            >
              <RotateCcw />
              还原
            </Button>
            <Button type="button" disabled={update.isPending} onClick={save}>
              {update.isPending ? <LoaderCircle className="animate-spin" /> : <Save />}
              保存简报
            </Button>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
