/**
 * InlineActions——对话内联操作卡片
 * 作为 assistant-ui ToolUI 注册，在对话中展示 CRUD 操作确认卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { makeAssistantToolUI } from "@assistant-ui/react"
import { Check, Pencil, Plus, Search, X } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

/** 创建实体确认卡片 */
interface CreateEntityCardProps {
  entityType: string
  entityName: string
  onConfirm: () => void
  onCancel: () => void
}

export function CreateEntityCard({
  entityType,
  entityName,
  onConfirm,
  onCancel
}: CreateEntityCardProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  return (
    <>
      <Card size="sm" className="my-2 max-w-sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Plus className="size-4 text-green-600" />
            创建{entityType}：{entityName}
          </CardTitle>
        </CardHeader>
        <CardFooter className="gap-2">
          <Button size="sm" onClick={() => setDialogOpen(true)}>
            <Check className="size-3.5" />
            确认创建
          </Button>
          <Button size="sm" variant="outline" onClick={onCancel}>
            <X className="size-3.5" />
            取消
          </Button>
        </CardFooter>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建{entityType}</DialogTitle>
            <DialogDescription>即将创建「{entityName}」，请确认信息无误。</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button
              onClick={() => {
                onConfirm()
                setDialogOpen(false)
              }}
            >
              确认
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

/** 查询结果内联卡片 */
interface QueryResultCardProps {
  title: string
  results: { id: string; label: string; description?: string }[]
  onItemClick?: (id: string) => void
}

export function QueryResultCard({ title, results, onItemClick }: QueryResultCardProps) {
  const displayResults = results.slice(0, 5)

  return (
    <Card size="sm" className="my-2 max-w-sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Search className="size-4 text-blue-600" />
          {title}（{results.length} 条结果）
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-1">
        {displayResults.map((item) => (
          <button
            key={item.id}
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted"
            onClick={() => onItemClick?.(item.id)}
          >
            <span className="font-medium">{item.label}</span>
            {item.description && (
              <span className="text-muted-foreground text-xs">{item.description}</span>
            )}
          </button>
        ))}
        {results.length > 5 && (
          <p className="px-2 text-muted-foreground text-xs">还有 {results.length - 5} 条结果…</p>
        )}
      </CardContent>
    </Card>
  )
}

/** 编辑确认卡片 */
interface EditConfirmCardProps {
  entityName: string
  fieldName: string
  oldValue: string
  newValue: string
  onConfirm: () => void
  onCancel: () => void
}

export function EditConfirmCard({
  entityName,
  fieldName,
  oldValue,
  newValue,
  onConfirm,
  onCancel
}: EditConfirmCardProps) {
  return (
    <Card size="sm" className="my-2 max-w-sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Pencil className="size-4 text-amber-600" />
          修改「{entityName}」的{fieldName}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground line-through">{oldValue}</span>
          <span>→</span>
          <span className="font-medium text-foreground">{newValue}</span>
        </div>
      </CardContent>
      <CardFooter className="gap-2">
        <Button size="sm" onClick={onConfirm}>
          <Check className="size-3.5" />
          确认修改
        </Button>
        <Button size="sm" variant="outline" onClick={onCancel}>
          <X className="size-3.5" />
          取消
        </Button>
      </CardFooter>
    </Card>
  )
}

/** assistant-ui ToolUI 注册：创建实体 */
export const CreateEntityToolUI = makeAssistantToolUI<
  { entityType: string; entityName: string },
  { success: boolean }
>({
  toolName: "create_entity",
  render: ({ args, status, result }) => {
    if (status.type === "complete" && result) {
      return (
        <Card size="sm" className="my-2 max-w-sm">
          <CardContent className="flex items-center gap-2 text-green-600 text-sm">
            <Check className="size-4" />
            已创建{args.entityType}「{args.entityName}」
          </CardContent>
        </Card>
      )
    }
    return (
      <CreateEntityCard
        entityType={args.entityType}
        entityName={args.entityName}
        onConfirm={() => {}} // TODO: 连接实际 mutation 逻辑
        onCancel={() => {}} // TODO: 连接实际取消逻辑
      />
    )
  }
})

/** assistant-ui ToolUI 注册：查询实体 */
export const QueryEntityToolUI = makeAssistantToolUI<
  { query: string; entityType: string },
  { results: { id: string; label: string; description?: string }[] }
>({
  toolName: "query_entity",
  render: ({ args, result }) => {
    const results = result?.results ?? []
    return <QueryResultCard title={`搜索${args.entityType}：${args.query}`} results={results} />
  }
})

/** assistant-ui ToolUI 注册：编辑实体 */
export const EditEntityToolUI = makeAssistantToolUI<
  { entityName: string; fieldName: string; oldValue: string; newValue: string },
  { success: boolean }
>({
  toolName: "edit_entity",
  render: ({ args, status, result }) => {
    if (status.type === "complete" && result?.success) {
      return (
        <Card size="sm" className="my-2 max-w-sm">
          <CardContent className="flex items-center gap-2 text-green-600 text-sm">
            <Check className="size-4" />
            已将「{args.entityName}」的{args.fieldName}修改为「{args.newValue}」
          </CardContent>
        </Card>
      )
    }
    return (
      <EditConfirmCard
        entityName={args.entityName}
        fieldName={args.fieldName}
        oldValue={args.oldValue}
        newValue={args.newValue}
        onConfirm={() => {}} // TODO: 连接实际 mutation 逻辑
        onCancel={() => {}} // TODO: 连接实际取消逻辑
      />
    )
  }
})
