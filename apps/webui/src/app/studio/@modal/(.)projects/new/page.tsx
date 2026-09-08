/**
 * Studio 新建项目拦截路由弹窗。
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { useCallback } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { NewProjectLauncher } from "@/features/studio/content"

export default function StudioProjectNewInterceptRoute() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const blueprintId = searchParams.get("blueprintId")
  const preselectedBlueprintId = blueprintId ? Number(blueprintId) : undefined

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (!open) router.back()
    },
    [router]
  )

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[calc(100vh-2rem)] w-full max-w-[calc(100%-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <DialogHeader className="sr-only">
          <DialogTitle>创建内容项目</DialogTitle>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-y-auto">
          <NewProjectLauncher
            mode="full"
            className="border-0 shadow-none"
            preselectedBlueprintId={
              Number.isFinite(preselectedBlueprintId) ? preselectedBlueprintId : undefined
            }
          />
        </div>
      </DialogContent>
    </Dialog>
  )
}
