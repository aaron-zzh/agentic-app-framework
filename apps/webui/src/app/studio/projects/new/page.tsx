/**
 * Content Studio 完整新建项目页。
 * @author AaronZZH & Kiro
 */

import { SectionHaze } from "@/components/studio"
import { NewProjectLauncher } from "@/features/studio/content"

export default function StudioProjectNewPage() {
  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="violet" />
      <div className="relative mx-auto flex max-w-4xl flex-col gap-6 p-6">
        <header className="flex flex-col gap-1">
          <h1 className="font-semibold text-2xl">创建内容项目</h1>
          <p className="text-muted-foreground text-sm">
            选择品牌、业务目标、生产模式与投放渠道，系统会立即物化项目结构。
          </p>
        </header>
        <NewProjectLauncher mode="full" />
      </div>
    </div>
  )
}
