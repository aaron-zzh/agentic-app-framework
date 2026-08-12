/**
 * 无限画布——tldraw 全屏绘图页
 *
 * @author AaronZZH & Kiro
 */

import { CanvasPanel } from "@/features/studio/projects/CanvasPanel"

interface StudioDrawPageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function StudioDrawPage({ searchParams }: StudioDrawPageProps) {
  const params = await searchParams
  const imageUrl = firstParam(params.imageUrl)

  return (
    <div className="h-full w-full">
      <CanvasPanel persistenceKey="studio-draw" initialImageUrl={imageUrl} />
    </div>
  )
}
