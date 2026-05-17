/**
 * DraggableBlockPlugin——块级拖放排序
 * @author AaronZZH & Kiro
 */

"use client"

import { DraggableBlockPlugin_EXPERIMENTAL } from "@lexical/react/LexicalDraggableBlockPlugin"
import { GripVertical } from "lucide-react"
import { useRef } from "react"

interface DraggableBlockPluginProps {
  anchorElem: HTMLElement
}

const DRAGGABLE_BLOCK_MENU_CLASSNAME = "draggable-block-menu"

export function DraggableBlockPlugin({ anchorElem }: DraggableBlockPluginProps) {
  const menuRef = useRef<HTMLDivElement>(null)
  const targetLineRef = useRef<HTMLDivElement>(null)

  return (
    <DraggableBlockPlugin_EXPERIMENTAL
      anchorElem={anchorElem}
      menuRef={menuRef}
      targetLineRef={targetLineRef}
      menuComponent={
        <div
          ref={menuRef}
          className={`${DRAGGABLE_BLOCK_MENU_CLASSNAME} absolute top-0 left-0 flex cursor-grab items-center rounded p-0.5 text-muted-foreground opacity-0 transition-opacity hover:bg-muted hover:opacity-100 active:cursor-grabbing`}
        >
          <GripVertical className="h-4 w-4" />
        </div>
      }
      targetLineComponent={
        <div
          ref={targetLineRef}
          className="pointer-events-none absolute top-0 left-0 h-0.5 bg-primary opacity-0"
        />
      }
      isOnMenu={(el) => el.closest(`.${DRAGGABLE_BLOCK_MENU_CLASSNAME}`) !== null}
    />
  )
}
