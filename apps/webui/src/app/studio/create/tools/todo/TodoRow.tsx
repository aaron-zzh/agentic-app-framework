/**
 * 待办行——切换完成状态、双击进入内联编辑、删除
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useRef, useState } from "react"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import {
  type TodoVO,
  useStudioTodoRemove,
  useStudioTodoUpdate,
  useStudioTodoUpdateStatus
} from "@/lib/api/rest/system/todo"
import { cn } from "@/lib/utils/cn"

export function TodoRow({ todo }: { todo: TodoVO }) {
  const [isEditing, setIsEditing] = useState(false)
  const [draftTitle, setDraftTitle] = useState(todo.title)
  const inputRef = useRef<HTMLInputElement>(null)

  const { mutate: updateStatus } = useStudioTodoUpdateStatus()
  const { mutate: updateTodo } = useStudioTodoUpdate()
  const { mutate: removeTodo } = useStudioTodoRemove()

  const isDone = todo.status === "done"

  const startEditing = () => {
    setDraftTitle(todo.title)
    setIsEditing(true)
    // 双击触发的 focus 需等 input 渲染后执行
    requestAnimationFrame(() => inputRef.current?.focus())
  }

  const commitEdit = () => {
    const trimmed = draftTitle.trim()
    setIsEditing(false)
    if (!trimmed) {
      removeTodo(todo.id)
      return
    }
    if (trimmed !== todo.title) {
      updateTodo({ id: todo.id, data: { title: trimmed } })
    }
  }

  const cancelEdit = () => {
    setDraftTitle(todo.title)
    setIsEditing(false)
  }

  return (
    <li
      className={cn(
        "group flex items-center gap-3 border-b border-dashed px-4 py-3 last:border-0",
        isDone && "bg-muted/30"
      )}
    >
      <Checkbox
        checked={isDone}
        onCheckedChange={(checked) =>
          updateStatus({ id: todo.id, status: checked ? "done" : "pending" })
        }
        aria-label={isDone ? "标记为未完成" : "标记为已完成"}
      />

      {isEditing ? (
        <Input
          ref={inputRef}
          value={draftTitle}
          onChange={(e) => setDraftTitle(e.target.value)}
          onBlur={commitEdit}
          onKeyDown={(e) => {
            if (e.key === "Enter") commitEdit()
            if (e.key === "Escape") cancelEdit()
          }}
          className="h-8 flex-1"
        />
      ) : (
        <button
          type="button"
          onDoubleClick={startEditing}
          onKeyDown={(e) => {
            if (e.key === "Enter") startEditing()
          }}
          className={cn(
            "flex-1 truncate text-left text-sm",
            isDone && "text-muted-foreground line-through"
          )}
        >
          {todo.title}
        </button>
      )}

      <button
        type="button"
        onClick={() => removeTodo(todo.id)}
        className="shrink-0 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
        aria-label="删除待办"
      >
        <X className="size-4" />
      </button>
    </li>
  )
}
