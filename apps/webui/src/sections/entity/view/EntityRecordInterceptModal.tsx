"use client"

import { useRouter } from "next/navigation"
import { useCallback } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ViewEngine } from "@/features/entity-engine/components"
import { entityRegistry } from "@/lib/modules/entity-registry"

interface EntityRecordInterceptModalProps {
  module: string
  recordId: string
  queryToken?: string
}

/** 工作区内导航到记录详情时展示的拦截路由弹窗。 */
export function EntityRecordInterceptModal({
  module,
  recordId,
  queryToken
}: EntityRecordInterceptModalProps) {
  const router = useRouter()
  const entity = entityRegistry.get(module)

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (!open) router.back()
    },
    [router]
  )

  if (!entity) return null

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[calc(100vh-2rem)] w-full max-w-[calc(100%-2rem)] flex-col gap-0 p-0 sm:max-w-6xl">
        <DialogHeader className="shrink-0 border-b px-5 py-4 pr-12">
          <DialogTitle>{entity.label}详情</DialogTitle>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-auto">
          <ViewEngine entity={entity} view="form" recordId={recordId} queryToken={queryToken} />
        </div>
      </DialogContent>
    </Dialog>
  )
}
