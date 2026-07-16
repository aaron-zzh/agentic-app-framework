/**
 * 工具-待办清单（TodoMVC 风格）
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckSquare } from "lucide-react"
import { useMemo, useState } from "react"
import { GlassCard } from "@/components/studio"
import { Checkbox } from "@/components/ui/checkbox"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import {
  useStudioTodoClearDone,
  useStudioTodos,
  useStudioTodoUpdateStatus
} from "@/lib/api/rest/system/todo"
import { type TodoFilter, TodoFooter } from "./TodoFooter"
import { TodoNewForm } from "./TodoNewForm"
import { TodoRow } from "./TodoRow"

export default function StudioTodoToolPage() {
  const [filter, setFilter] = useState<TodoFilter>("all")
  const { data, isLoading } = useStudioTodos()
  const { mutate: updateStatus } = useStudioTodoUpdateStatus()
  const { mutate: clearDone } = useStudioTodoClearDone()

  const todos = data?.list ?? []
  const activeTodos = todos.filter((t) => t.status !== "done")
  const completedTodos = todos.filter((t) => t.status === "done")

  const visibleTodos = useMemo(() => {
    if (filter === "active") return activeTodos
    if (filter === "completed") return completedTodos
    return todos
  }, [filter, todos, activeTodos, completedTodos])

  const allDone = todos.length > 0 && activeTodos.length === 0

  const handleToggleAll = (checked: boolean) => {
    const target = checked ? activeTodos : completedTodos
    target.forEach((todo) => {
      updateStatus({ id: todo.id, status: checked ? "done" : "pending" })
    })
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <CheckSquare className="size-5 text-emerald-400" />
        <h1 className="font-semibold text-xl">待办清单</h1>
      </header>

      <GlassCard glow="accent">
        <div className="space-y-3 p-5">
          <TodoNewForm />

          {isLoading ? (
            <div className="space-y-3 px-1 py-2">
              <Skeleton className="h-5 w-full" />
              <Skeleton className="h-5 w-4/5" />
              <Skeleton className="h-5 w-3/5" />
            </div>
          ) : todos.length === 0 ? (
            <Empty className="py-10">
              <EmptyHeader>
                <EmptyTitle>暂无待办</EmptyTitle>
                <EmptyDescription>在上方输入框添加一条待办，按 Enter 即可创建</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="rounded-lg border">
              <div className="flex items-center gap-3 border-b border-dashed px-4 py-2">
                <Checkbox
                  checked={allDone}
                  onCheckedChange={(checked) => handleToggleAll(checked === true)}
                  aria-label="全部标记为完成"
                />
                <span className="text-muted-foreground text-xs">全部标记为完成</span>
              </div>

              <ScrollArea className="max-h-[480px]">
                <ul>
                  {visibleTodos.map((todo) => (
                    <TodoRow key={todo.id} todo={todo} />
                  ))}
                </ul>
              </ScrollArea>

              <TodoFooter
                activeCount={activeTodos.length}
                completedCount={completedTodos.length}
                filter={filter}
                onFilterChange={setFilter}
                onClearCompleted={() => clearDone(completedTodos.map((t) => t.id))}
              />
            </div>
          )}
        </div>
      </GlassCard>
    </div>
  )
}
