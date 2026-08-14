/**
 * Studio 素材生成统一入口。
 *
 * 图像、视频、配音、音乐和 3D 共用当前页面，通过 mode 查询参数区分；
 * 数字人保持不可提交的独立占位分支。
 *
 * @author AaronZZH & Kiro
 */

import { UserRound } from "lucide-react"
import { SectionHaze } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"
import type {
  MediaGenerationDraft,
  MediaGenerationMode
} from "@/features/studio/media-generation/types"
import { isMediaGenerationMode } from "@/features/studio/media-generation/types"

interface StudioCreatePageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

function DigitalHumanPlaceholder() {
  return (
    <div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6">
      <SectionHaze variant="violet" />
      <header className="relative flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <p className="font-medium text-primary text-sm">Studio Create</p>
          <Badge variant="secondary">尚未开放</Badge>
        </div>
        <h1 className="font-semibold text-2xl">数字人创作</h1>
        <p className="max-w-3xl text-muted-foreground text-sm">
          数字人能力正在准备中。本页面仅用于确认创作模式，当前不会提交生成任务。
        </p>
      </header>
      <Card className="relative">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UserRound aria-hidden="true" />
            数字人能力尚未开放
          </CardTitle>
          <CardDescription>
            后续将在这里提供形象、声音与视频驱动能力；开放前不会创建模拟任务或结果。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm">你可以先使用图像、视频、配音等现有能力。</p>
        </CardContent>
      </Card>
    </div>
  )
}

export default async function StudioCreatePage({ searchParams }: StudioCreatePageProps) {
  const params = await searchParams
  const rawMode = firstParam(params.mode)
  if (rawMode === "digital-human") return <DigitalHumanPlaceholder />

  const mode: MediaGenerationMode = isMediaGenerationMode(rawMode) ? rawMode : "image"
  const prompt = firstParam(params.prompt) ?? ""
  const initialDraft: MediaGenerationDraft | undefined = prompt
    ? {
        revision: 0,
        prompt
      }
    : undefined

  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="violet" />
      <div className="relative pt-4">
        <MediaGenerationWorkspace initialMode={mode} initialDraft={initialDraft} />
      </div>
    </div>
  )
}
