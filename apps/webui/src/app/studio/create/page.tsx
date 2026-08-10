/**
 * Studio 统一创作入口。
 *
 * 基础媒体生成进入统一任务与媒体持久化链路；文案属于内容对象，不进入媒体资产库。
 * @author AaronZZH & Kiro
 */

import { Box, FileText, ImageIcon, Mic, Music, UserRound, Video } from "lucide-react"
import Link from "next/link"
import { SectionHaze } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/components/ui/card"

const MEDIA_ENTRIES = [
  {
    title: "图像",
    description: "文生图与参考图编辑，生成结果进入媒体库。",
    href: "/studio/create/image",
    icon: ImageIcon
  },
  {
    title: "视频",
    description: "支持文生视频、图生视频与首尾帧创作。",
    href: "/studio/create/video",
    icon: Video
  },
  {
    title: "配音",
    description: "输入文本并选择音色，生成可复用音频。",
    href: "/studio/create/voice",
    icon: Mic
  },
  {
    title: "音乐",
    description: "根据主题、风格或歌词生成完整音乐。",
    href: "/studio/create/music",
    icon: Music
  },
  {
    title: "3D",
    description: "通过文字描述生成基础 3D 模型素材。",
    href: "/studio/create/tools/3d",
    icon: Box
  }
] as const

interface StudioCreatePageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>
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
  const mode = Array.isArray(params.mode) ? params.mode[0] : params.mode
  if (mode === "digital-human") return <DigitalHumanPlaceholder />

  return (
    <div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6">
      <SectionHaze variant="violet" />
      <header className="relative flex flex-col gap-2">
        <p className="font-medium text-primary text-sm">Studio Create</p>
        <h1 className="font-semibold text-2xl">从一个入口开始创作</h1>
        <p className="max-w-3xl text-muted-foreground text-sm">
          图像、视频、配音、音乐和 3D
          统一进入生成任务与媒体库；确认有复用价值后，可将媒体保存为资产。
        </p>
      </header>

      <section className="relative grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {MEDIA_ENTRIES.map((entry) => {
          const Icon = entry.icon
          return (
            <Card key={entry.href} className="transition-shadow hover:shadow-md">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Icon aria-hidden="true" />
                  {entry.title}
                </CardTitle>
                <CardDescription>{entry.description}</CardDescription>
                <CardAction>
                  <Button
                    nativeButton={false}
                    variant="outline"
                    render={<Link href={entry.href} />}
                  >
                    开始创作
                  </Button>
                </CardAction>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-xs">生成结果：Media → 可选保存为 Asset</p>
              </CardContent>
            </Card>
          )
        })}
      </section>

      <Card className="relative flex-row flex-wrap items-center p-4">
        <FileText aria-hidden="true" className="shrink-0" />
        <div className="min-w-0 flex-1">
          <CardTitle>文案是内容对象</CardTitle>
          <CardDescription>文案进入内容创作与项目编排流程，不作为媒体资产保存。</CardDescription>
        </div>
        <Button
          nativeButton={false}
          variant="secondary"
          render={<Link href="/studio/create/copy" />}
        >
          进入文案创作
        </Button>
      </Card>
    </div>
  )
}
