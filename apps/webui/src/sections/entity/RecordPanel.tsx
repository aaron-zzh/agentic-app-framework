/**
 * RecordPanel——记录快速查看面板
 * @author AaronZZH & Kiro
 *
 * 移动端（<768px）：底部 Drawer
 * 桌面端（≥768px）：右侧 Resizable 面板，左侧保留列表
 */

"use client"

import { X } from "lucide-react"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { ViewEngine } from "@/features/entity-engine/components"
import {
  RecordWindowNavigationControls,
  useRecordWindowNavigation
} from "@/features/entity-engine/components/ViewEngine"
import type { EntityDef } from "@/features/entity-engine/types"

interface Props {
  entity: EntityDef
  recordId: string
  onClose: () => void
  /** 左侧列表内容（桌面端并排显示） */
  children: React.ReactNode
  /** panel=侧边面板（默认）drawer=强制底部抽屉 */
  mode?: "panel" | "drawer"
  /** 详情所属查询窗口标识 */
  queryToken?: string
  /** 在保持面板打开的前提下切换详情记录 */
  onRecordChange?: (recordId: string) => void
}

export function RecordPanel({
  entity,
  recordId,
  onClose,
  children,
  mode = "panel",
  queryToken,
  onRecordChange
}: Props) {
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 768)
    check()
    window.addEventListener("resize", check)
    return () => window.removeEventListener("resize", check)
  }, [])

  const recordWindowNavigation = useRecordWindowNavigation({
    entity,
    recordId,
    queryToken,
    onRecordChange
  })

  const detail = (
    <ViewEngine
      entity={entity}
      view="form"
      recordId={recordId}
      queryToken={queryToken}
      readOnly
      onRecordChange={onRecordChange}
      showRecordWindowPager={false}
    />
  )

  // drawer 模式：右侧 Sheet，手机端全屏
  if (mode === "drawer" || isMobile) {
    return (
      <>
        {children}
        <Sheet open onOpenChange={(open) => !open && onClose()}>
          <SheetContent side="right" className="sm:!max-w-[620px] flex w-full flex-col p-0">
            <SheetHeader className="border-b px-4 py-3">
              <div className="flex min-w-0 items-center justify-between gap-2 pr-8">
                <SheetTitle className="truncate">{entity.label}详情</SheetTitle>
                {queryToken && recordWindowNavigation.isAvailable && (
                  <RecordWindowNavigationControls navigation={recordWindowNavigation} />
                )}
              </div>
              {queryToken && recordWindowNavigation.isAvailable && (
                <p className="text-muted-foreground text-xs">
                  {recordWindowNavigation.statusLabel}
                </p>
              )}
            </SheetHeader>
            <div className="flex-1 overflow-auto">{detail}</div>
          </SheetContent>
        </Sheet>
      </>
    )
  }

  // 桌面端：左侧列表 + 右侧详情面板
  return (
    <ResizablePanelGroup orientation="horizontal" className="h-full">
      <ResizablePanel defaultSize="55%" minSize="25%">
        {children}
      </ResizablePanel>

      <ResizableHandle withHandle />

      <ResizablePanel defaultSize="45%" minSize="20%">
        <div className="flex h-full flex-col">
          <div className="shrink-0 border-b px-4 py-2">
            <div className="flex min-w-0 items-center justify-between gap-2">
              <span className="truncate font-medium text-sm">{entity.label}详情</span>
              <div className="flex shrink-0 items-center gap-2">
                {queryToken && recordWindowNavigation.isAvailable && (
                  <RecordWindowNavigationControls navigation={recordWindowNavigation} />
                )}
                <Button variant="ghost" size="icon" className="size-7" onClick={onClose}>
                  <X className="size-4" />
                </Button>
              </div>
            </div>
            {queryToken && recordWindowNavigation.isAvailable && (
              <p className="text-muted-foreground text-xs">{recordWindowNavigation.statusLabel}</p>
            )}
          </div>
          <div className="flex-1 overflow-auto">{detail}</div>
        </div>
      </ResizablePanel>
    </ResizablePanelGroup>
  )
}
