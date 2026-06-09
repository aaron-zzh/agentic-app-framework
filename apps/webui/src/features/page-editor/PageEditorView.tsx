/**
 * PageEditorView——页面编辑器主视图
 * @author AaronZZH & Kiro
 *
 * 三栏布局：左侧区块列表（拖拽排序）+ 中间实时预览 + 右侧属性面板
 * 使用 @dnd-kit 实现拖拽排序
 */

"use client"

import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { GripVertical, Plus, Trash2 } from "lucide-react"
import { useCallback, useState } from "react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils/cn"

import { getAllSectionTypes, PageEngine } from "../page-engine"
import type { PageDef, SectionDef } from "../page-engine/types"
import { SectionPropsPanel } from "./SectionPropsPanel"

// ─── 可排序区块项 ─────────────────────────────────────────────────────────────

interface SortableItemProps {
  section: SectionDef
  isSelected: boolean
  onSelect: () => void
  onDelete: () => void
}

function SortableItem({ section, isSelected, onSelect, onDelete }: SortableItemProps) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({
    id: section.id
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex items-center gap-2 rounded-md border px-3 py-2 text-sm",
        isSelected ? "border-primary bg-primary/5" : "border-border hover:bg-accent/50"
      )}
    >
      <button
        type="button"
        className="cursor-grab text-muted-foreground hover:text-foreground"
        {...attributes}
        {...listeners}
        aria-label="拖拽排序"
      >
        <GripVertical className="size-4" />
      </button>
      <button type="button" className="flex-1 text-left" onClick={onSelect}>
        <span className="font-medium">{section.type}</span>
        <span className="ml-2 text-muted-foreground text-xs">#{section.id}</span>
      </button>
      <button
        type="button"
        onClick={onDelete}
        className="text-muted-foreground hover:text-destructive"
        aria-label="删除区块"
      >
        <Trash2 className="size-3.5" />
      </button>
    </div>
  )
}

// ─── 添加区块对话框 ──────────────────────────────────────────────────────────

interface AddSectionDialogProps {
  onAdd: (type: string) => void
}

function AddSectionDialog({ onAdd }: AddSectionDialogProps) {
  const types = getAllSectionTypes()

  return (
    <Dialog>
      <DialogTrigger
        render={
          <Button variant="outline" size="sm" className="w-full">
            <Plus className="mr-1 size-4" />
            添加区块
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>选择区块类型</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-2">
          {types.map(({ type, label }) => (
            <Button
              key={type}
              variant="outline"
              size="sm"
              onClick={() => onAdd(type)}
              className="justify-start"
            >
              {label}
            </Button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ─── 主编辑器 ────────────────────────────────────────────────────────────────

interface PageEditorViewProps {
  /** 初始 PageDef */
  initialPage: PageDef
  /** 保存回调 */
  onSave?: (page: PageDef) => void
  /** 发布回调 */
  onPublish?: (page: PageDef) => void
}

/** 页面编辑器主视图 */
export function PageEditorView({ initialPage, onSave, onPublish }: PageEditorViewProps) {
  const [page, setPage] = useState<PageDef>(initialPage)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  )

  const selectedSection = page.sections.find((s) => s.id === selectedId)

  /** 拖拽结束——重新排序 */
  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event
      if (!over || active.id === over.id) return

      const oldIndex = page.sections.findIndex((s) => s.id === active.id)
      const newIndex = page.sections.findIndex((s) => s.id === over.id)
      setPage((prev) => ({
        ...prev,
        sections: arrayMove(prev.sections, oldIndex, newIndex)
      }))
    },
    [page.sections]
  )

  /** 添加区块 */
  const handleAdd = useCallback((type: string) => {
    const id = `${type}-${Date.now()}`
    const newSection: SectionDef = { id, type, props: {} }
    setPage((prev) => ({ ...prev, sections: [...prev.sections, newSection] }))
    setSelectedId(id)
    toast.success(`已添加「${type}」区块`)
  }, [])

  /** 删除区块 */
  const handleDelete = useCallback(
    (id: string) => {
      setPage((prev) => ({
        ...prev,
        sections: prev.sections.filter((s) => s.id !== id)
      }))
      if (selectedId === id) setSelectedId(null)
    },
    [selectedId]
  )

  /** 更新区块属性 */
  const handleSectionChange = useCallback((updated: SectionDef) => {
    setPage((prev) => ({
      ...prev,
      sections: prev.sections.map((s) => (s.id === updated.id ? updated : s))
    }))
  }, [])

  return (
    <div className="flex h-full flex-col">
      {/* 工具栏 */}
      <div className="flex items-center justify-between border-b px-4 py-2">
        <h2 className="font-semibold text-sm">{page.title}</h2>
        <div className="flex gap-2">
          {onSave && (
            <Button size="sm" variant="outline" onClick={() => onSave(page)}>
              保存
            </Button>
          )}
          {onPublish && (
            <Button size="sm" onClick={() => onPublish(page)}>
              发布
            </Button>
          )}
        </div>
      </div>

      {/* 三栏布局 */}
      <ResizablePanelGroup orientation="horizontal" className="flex-1">
        {/* 左侧：区块列表 */}
        <ResizablePanel defaultSize="20%" minSize="15%">
          <ScrollArea className="h-full">
            <div className="space-y-2 p-3">
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={handleDragEnd}
              >
                <SortableContext
                  items={page.sections.map((s) => s.id)}
                  strategy={verticalListSortingStrategy}
                >
                  {page.sections.map((section) => (
                    <SortableItem
                      key={section.id}
                      section={section}
                      isSelected={section.id === selectedId}
                      onSelect={() => setSelectedId(section.id)}
                      onDelete={() => handleDelete(section.id)}
                    />
                  ))}
                </SortableContext>
              </DndContext>
              <AddSectionDialog onAdd={handleAdd} />
            </div>
          </ScrollArea>
        </ResizablePanel>

        <ResizableHandle />

        {/* 中间：实时预览 */}
        <ResizablePanel defaultSize="55%">
          <ScrollArea className="h-full">
            <div className="min-h-full bg-background">
              <PageEngine page={page} />
            </div>
          </ScrollArea>
        </ResizablePanel>

        <ResizableHandle />

        {/* 右侧：属性面板 */}
        <ResizablePanel defaultSize="25%" minSize="20%">
          <ScrollArea className="h-full">
            {selectedSection ? (
              <SectionPropsPanel section={selectedSection} onChange={handleSectionChange} />
            ) : (
              <div className="flex h-full items-center justify-center p-4 text-center text-muted-foreground text-sm">
                点击左侧区块编辑属性
              </div>
            )}
          </ScrollArea>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
