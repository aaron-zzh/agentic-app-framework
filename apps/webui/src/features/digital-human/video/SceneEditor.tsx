"use client"

import { useState } from "react"
import { GripVertical, Plus, Trash, Play } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"

export interface Scene {
  id: string
  order: number
  title: string
  content: string
  duration: number
  sceneDescription?: string
}

interface SceneEditorProps {
  value: Scene[]
  onChange: (scenes: Scene[]) => void
  disabled?: boolean
}

export function SceneEditor({ value, onChange, disabled }: SceneEditorProps) {
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates
    })
  )

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = value.findIndex((s) => s.id === active.id)
    const newIndex = value.findIndex((s) => s.id === over.id)
    const reordered = arrayMove(value, oldIndex, newIndex)
    onChange(reordered.map((s, i) => ({ ...s, order: i + 1 })))
  }

  const addScene = () => {
    const newScene: Scene = {
      id: `scene-${Date.now()}`,
      order: value.length + 1,
      title: `场景 ${value.length + 1}`,
      content: "",
      duration: 10
    }
    onChange([...value, newScene])
  }

  const removeScene = (id: string) => {
    onChange(value.filter((s) => s.id !== id))
  }

  const updateScene = (id: string, updates: Partial<Scene>) => {
    onChange(value.map((s) => (s.id === id ? { ...s, ...updates } : s)))
  }

  const totalDuration = value.reduce((sum, s) => sum + s.duration, 0)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="text-muted-foreground text-sm">
          共 {value.length} 个场景，总时长 {totalDuration} 秒
        </div>
        <Button onClick={addScene} disabled={disabled} size="sm">
          <Plus className="mr-2 size-4" />
          添加场景
        </Button>
      </div>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={value.map((s) => s.id)} strategy={verticalListSortingStrategy}>
          <div className="space-y-3">
            {value.map((scene) => (
              <SortableSceneCard
                key={scene.id}
                scene={scene}
                disabled={disabled}
                onUpdate={(updates) => updateScene(scene.id, updates)}
                onRemove={() => removeScene(scene.id)}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      {value.length === 0 && (
        <Card className="border-dashed">
          <CardContent className="py-12 text-center">
            <p className="mb-4 text-muted-foreground">暂无场景</p>
            <Button onClick={addScene} disabled={disabled}>
              添加第一个场景
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function SortableSceneCard({
  scene,
  disabled,
  onUpdate,
  onRemove
}: {
  scene: Scene
  disabled?: boolean
  onUpdate: (updates: Partial<Scene>) => void
  onRemove: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: scene.id,
    disabled
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1
  }

  return (
    <Card ref={setNodeRef} style={style}>
      <CardHeader className="flex flex-row items-center space-y-0 pb-3">
        <div className="flex flex-1 items-center gap-3">
          <button
            className="cursor-grab touch-none text-muted-foreground hover:text-foreground"
            {...attributes}
            {...listeners}
          >
            <GripVertical className="size-5" />
          </button>
          <Input
            value={scene.title}
            onChange={(e) => onUpdate({ title: e.target.value })}
            disabled={disabled}
            className="max-w-xs"
            placeholder="场景标题"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon-sm" disabled>
            <Play className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onRemove}
            disabled={disabled}
            className="text-destructive"
          >
            <Trash className="size-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div>
          <Label htmlFor={`content-${scene.id}`}>播报内容</Label>
          <Textarea
            id={`content-${scene.id}`}
            value={scene.content}
            onChange={(e) => onUpdate({ content: e.target.value })}
            disabled={disabled}
            rows={3}
            placeholder="输入数字人播报的文案内容..."
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor={`duration-${scene.id}`}>时长（秒）</Label>
            <Input
              id={`duration-${scene.id}`}
              type="number"
              value={scene.duration}
              onChange={(e) => onUpdate({ duration: parseInt(e.target.value) || 0 })}
              disabled={disabled}
              min={1}
              max={15}
            />
          </div>
          <div>
            <Label htmlFor={`desc-${scene.id}`}>场景描述（可选）</Label>
            <Input
              id={`desc-${scene.id}`}
              value={scene.sceneDescription || ""}
              onChange={(e) => onUpdate({ sceneDescription: e.target.value })}
              disabled={disabled}
              placeholder="如：正面特写、微笑"
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
