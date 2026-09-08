/**
 * Content Studio 完整新建项目页。
 * @author AaronZZH & Kiro
 */

import { SectionHaze } from "@/components/studio"
import { NewProjectLauncher } from "@/features/studio/content"

export default async function StudioProjectNewPage({
  searchParams
}: {
  searchParams: Promise<{ blueprintId?: string }>
}) {
  const { blueprintId } = await searchParams
  const preselectedBlueprintId = blueprintId ? Number(blueprintId) : undefined

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="violet" />
      <div className="relative mx-auto flex max-w-4xl flex-col gap-6 p-6">
        <header>
          <h1 className="font-semibold text-2xl">创建内容项目</h1>
        </header>
        <NewProjectLauncher
          mode="full"
          preselectedBlueprintId={
            Number.isFinite(preselectedBlueprintId) ? preselectedBlueprintId : undefined
          }
        />
      </div>
    </div>
  )
}
