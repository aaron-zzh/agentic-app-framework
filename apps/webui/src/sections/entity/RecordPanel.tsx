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
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle
} from "@/components/ui/drawer"
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup
} from "@/components/ui/resizable"
import { ViewEngine } from "@/features/entity-engine/components"
import type { EntityDef } from "@/features/entity-engine/types"

interface Props {
  entity: EntityDef
  recordId: string
  onClose: () => void
  /** 左侧列表内容（桌面端并排显示） */
  children: React.ReactNode
}

export function RecordPanel({ entity, recordId, onClose, children }: Props) {
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 768)
    check()
    window.addEventListener("resize", check)
    return () => window.removeEventListener("resize", check)
  }, [])

  const detail = <ViewEngine entity={entity} view="form" recordId={recordId} />

  // 移动端：底部 Drawer，列表保持可见
  if (isMobile) {
    return (
      <>
        {children}
        <Drawer open onClose={onClose} direction="bottom">
          <DrawerContent className="max-h-[85vh]">
            <DrawerHeader className="flex items-center justify-between border-b">
              <DrawerTitle>{entity.label}详情</DrawerTitle>
              <Button variant="ghost" size="icon" onClick={onClose}>
                <X className="size-4" />
              </Button>
            </DrawerHeader>
            <div className="flex-1 overflow-auto">{detail}</div>
          </DrawerContent>
        </Drawer>
      </>
    )
  }

  // 桌面端：左侧列表 + 右侧详情面板
  return (
    <ResizablePanelGroup orientation="horizontal" className="h-full">
      <ResizablePanel defaultSize={55} minSize={25}>
        {children}
      </ResizablePanel>

      <ResizableHandle withHandle />

      <ResizablePanel defaultSize={45} minSize={20}>
        <div className="flex h-full flex-col">
          <div className="flex h-10 shrink-0 items-center justify-between border-b px-4">
            <span className="font-medium text-sm">{entity.label}详情</span>
            <Button variant="ghost" size="icon" className="size-7" onClick={onClose}>
              <X className="size-4" />
            </Button>
          </div>
          <div className="flex-1 overflow-auto">{detail}</div>
        </div>
      </ResizablePanel>
    </ResizablePanelGroup>
  )
}
