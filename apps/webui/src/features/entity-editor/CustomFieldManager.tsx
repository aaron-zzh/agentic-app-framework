/**
 * 自定义字段管理面板——字段列表 + 添加/隐藏操作
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  CalendarDays,
  CaseSensitive,
  CheckSquare,
  EyeOff,
  Hash,
  List,
  Plus,
  Settings
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { customFieldApi, type CustomFieldRecord, type CustomFieldType } from "@/lib/api/custom-field"
import { AddCustomFieldDialog } from "./AddCustomFieldDialog"

/** 类型图标映射 */
const TYPE_ICONS: Record<CustomFieldType, React.ReactNode> = {
  text: <CaseSensitive className="size-4 text-muted-foreground" />,
  number: <Hash className="size-4 text-muted-foreground" />,
  date: <CalendarDays className="size-4 text-muted-foreground" />,
  select: <List className="size-4 text-muted-foreground" />,
  boolean: <CheckSquare className="size-4 text-muted-foreground" />
}

/** 类型中文标签 */
const TYPE_LABELS: Record<CustomFieldType, string> = {
  text: "文本",
  number: "数字",
  date: "日期",
  select: "下拉选择",
  boolean: "布尔"
}

interface CustomFieldManagerProps {
  /** 实体 slug */
  slug: string
}

/** 自定义字段管理面板 */
export function CustomFieldManager({ slug }: CustomFieldManagerProps) {
  const [managerOpen, setManagerOpen] = useState(false)
  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const queryClient = useQueryClient()

  const { data: fields = [], isLoading } = useQuery({
    queryKey: ["custom-fields", slug],
    queryFn: () => customFieldApi.list(slug),
    enabled: managerOpen
  })

  const hideMutation = useMutation({
    mutationFn: (fieldName: string) => customFieldApi.hide(slug, fieldName),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["entity-defs"] })
      queryClient.invalidateQueries({ queryKey: ["custom-fields", slug] })
    }
  })

  const handleHide = useCallback(
    (fieldName: string) => {
      hideMutation.mutate(fieldName)
    },
    [hideMutation]
  )

  return (
    <>
      {/* 入口按钮 */}
      <Dialog open={managerOpen} onOpenChange={setManagerOpen}>
        <DialogTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <Settings className="size-4" />
          <span className="sr-only">自定义字段</span>
        </DialogTrigger>

        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>自定义字段管理</DialogTitle>
          </DialogHeader>

          <div className="space-y-3">
            {/* 字段列表 */}
            {isLoading ? (
              <div className="py-8 text-center text-sm text-muted-foreground">加载中...</div>
            ) : fields.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                暂无自定义字段
              </div>
            ) : (
              <div className="max-h-80 space-y-1 overflow-y-auto">
                {fields.map((field) => (
                  <FieldItem
                    key={field.name}
                    field={field}
                    onHide={handleHide}
                    hiding={hideMutation.isPending}
                  />
                ))}
              </div>
            )}

            {/* 添加按钮 */}
            <Button
              variant="outline"
              className="w-full"
              onClick={() => setAddDialogOpen(true)}
            >
              <Plus className="mr-1 size-4" />
              添加字段
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 添加字段弹窗 */}
      <AddCustomFieldDialog
        slug={slug}
        open={addDialogOpen}
        onOpenChange={setAddDialogOpen}
      />
    </>
  )
}

/** 单个字段行 */
function FieldItem({
  field,
  onHide,
  hiding
}: {
  field: CustomFieldRecord
  onHide: (name: string) => void
  hiding: boolean
}) {
  return (
    <div
      className={`flex items-center justify-between rounded-md border px-3 py-2 ${
        field.hidden ? "opacity-50" : ""
      }`}
    >
      <div className="flex items-center gap-2">
        {TYPE_ICONS[field.type]}
        <div>
          <span className="text-sm font-medium">{field.label}</span>
          <span className="ml-2 text-xs text-muted-foreground">{field.name}</span>
        </div>
        <Badge variant="secondary" className="text-xs">
          {TYPE_LABELS[field.type]}
        </Badge>
        {field.hidden && (
          <Badge variant="outline" className="text-xs text-muted-foreground">
            已隐藏
          </Badge>
        )}
      </div>

      {!field.hidden && (
        <Button
          variant="ghost"
          size="icon-xs"
          onClick={() => onHide(field.name)}
          disabled={hiding}
        >
          <EyeOff className="size-3.5" />
          <span className="sr-only">隐藏字段</span>
        </Button>
      )}
    </div>
  )
}
