/**
 * GlobalDndContext——全局拖放上下文
 *
 * 统一处理所有跨区域拖放：
 * - chatter-composer-drop   → 写入 chatter-store.pendingDropItem（对话附件）
 * - generation-drop-zone    → aigc store addReferenceAsset
 * - storyboard-drop-zone    → aigc store addStoryboardAsset
 * - group-{id}              → 移动素材到目标素材组（调 API，invalidate media-assets）
 *
 * 未来其他页面的元素只需注册对应 droppable id 即可复用此 handler。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { DndContext, type DragEndEvent, useSensor, useSensors } from "@dnd-kit/core"
import { useQueryClient } from "@tanstack/react-query"
import type { ReactNode } from "react"
import { SmartPointerSensor } from "@/features/aigc/SmartPointerSensor"
import { useAigcStore } from "@/features/aigc/store"
import { mediaAssetApi } from "@/lib/api/rest/media/media-asset"
import { useChatterStore } from "@/lib/store/chatter-store"

interface GlobalDndContextProps {
  children: ReactNode
}

export function GlobalDndContext({ children }: GlobalDndContextProps) {
  const setPendingDropItem = useChatterStore((s) => s.setPendingDropItem)
  const queryClient = useQueryClient()

  const sensors = useSensors(
    useSensor(SmartPointerSensor, { activationConstraint: { distance: 8 } })
  )

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over || !active.data.current) return

    const item = active.data.current
    const overId = String(over.id)

    // 拖到对话框
    if (overId === "chatter-composer-drop") {
      setPendingDropItem(item as Parameters<typeof setPendingDropItem>[0])
      return
    }

    // 以下 aigc 相关操作
    const aigc = useAigcStore.getState()

    if (overId === "generation-drop-zone") {
      aigc.addReferenceAsset(item)
      return
    }

    if (overId === "storyboard-drop-zone") {
      aigc.addStoryboardAsset(item)
      return
    }
    if (overId.startsWith("group-")) {
      const targetGroupId = Number(overId.replace("group-", ""))
      const assetId = Number(item.id)
      const currentGroupId = item.groupId as number | undefined
      if (
        !Number.isNaN(targetGroupId) &&
        !Number.isNaN(assetId) &&
        currentGroupId !== targetGroupId
      ) {
        mediaAssetApi.moveToGroup(assetId, targetGroupId).then(() => {
          queryClient.invalidateQueries({ queryKey: ["media-assets"] })
        })
      }
    }
  }

  return (
    <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
      {children}
    </DndContext>
  )
}
