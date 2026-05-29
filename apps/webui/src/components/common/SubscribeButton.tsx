/**
 * SubscribeButton——字段变更订阅按钮 + 配置弹窗
 * 表单顶部 [👁 关注] 按钮，点击弹出订阅配置（字段勾选 + 通道选择）
 * @author AaronZZH & Kiro
 */

"use client"

import { Eye, EyeOff } from "lucide-react"
import { useId, useState } from "react"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import type { SubscriptionChannel } from "@/lib/api/subscription"
import {
  useRemoveSubscription,
  useSubscription,
  useUpsertSubscription
} from "@/lib/queries/use-subscription"
import type { DataFieldDef, EntityDef, FieldDef } from "@/lib/types/entity"

interface SubscribeButtonProps {
  entity: EntityDef
  recordId: string
}

/** 获取实体中可订阅的数据字段（排除布局字段） */
function getDataFields(fields: FieldDef[]): DataFieldDef[] {
  return fields.filter(
    (f): f is DataFieldDef => f.type !== "group" && f.type !== "tabs" && f.type !== "row"
  )
}

export function SubscribeButton({ entity, recordId }: SubscribeButtonProps) {
  const formId = useId()
  const [open, setOpen] = useState(false)
  const { data: subscription } = useSubscription(entity.slug, recordId)
  const upsert = useUpsertSubscription()
  const remove = useRemoveSubscription()

  const isSubscribed = !!subscription

  // 弹窗内部状态
  const [allFields, setAllFields] = useState(true)
  const [selectedFields, setSelectedFields] = useState<string[]>([])
  const [channels, setChannels] = useState<SubscriptionChannel[]>(["inApp"])

  /** 打开弹窗时初始化状态 */
  function handleOpen() {
    if (subscription) {
      setAllFields(subscription.fields === null)
      setSelectedFields(subscription.fields ?? [])
      setChannels(subscription.channels)
    } else {
      setAllFields(true)
      setSelectedFields([])
      setChannels(["inApp"])
    }
    setOpen(true)
  }

  /** 保存订阅 */
  function handleSave() {
    upsert.mutate({
      entityType: entity.slug,
      entityId: recordId,
      fields: allFields ? null : selectedFields,
      channels
    })
    setOpen(false)
  }

  /** 取消订阅 */
  function handleUnsubscribe() {
    remove.mutate({ entityType: entity.slug, entityId: recordId })
    setOpen(false)
  }

  /** 切换字段勾选 */
  function toggleField(name: string) {
    setSelectedFields((prev) =>
      prev.includes(name) ? prev.filter((f) => f !== name) : [...prev, name]
    )
  }

  /** 切换通道 */
  function toggleChannel(ch: SubscriptionChannel) {
    setChannels((prev) => (prev.includes(ch) ? prev.filter((c) => c !== ch) : [...prev, ch]))
  }

  const dataFields = getDataFields(entity.fields)

  return (
    <>
      <Button variant="ghost" size="sm" onClick={handleOpen}>
        {isSubscribed ? <Eye className="mr-1 h-4 w-4" /> : <EyeOff className="mr-1 h-4 w-4" />}
        {isSubscribed ? "已关注" : "关注"}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>关注此记录的变更</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* 字段选择 */}
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Checkbox
                  id={`${formId}-all-fields`}
                  checked={allFields}
                  onCheckedChange={(v) => setAllFields(!!v)}
                />
                <Label htmlFor={`${formId}-all-fields`}>所有字段变更</Label>
              </div>

              {!allFields && (
                <div className="ml-6 space-y-1">
                  {dataFields.map((field) => (
                    <div key={field.name} className="flex items-center gap-2">
                      <Checkbox
                        id={`field-${field.name}`}
                        checked={selectedFields.includes(field.name)}
                        onCheckedChange={() => toggleField(field.name)}
                      />
                      <Label htmlFor={`field-${field.name}`}>{field.label ?? field.name}</Label>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 通道选择 */}
            <div className="space-y-2">
              <span className="font-medium text-sm">通知方式</span>
              <div className="flex gap-4">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={`${formId}-ch-inApp`}
                    checked={channels.includes("inApp")}
                    onCheckedChange={() => toggleChannel("inApp")}
                  />
                  <Label htmlFor={`${formId}-ch-inApp`}>站内通知</Label>
                </div>
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={`${formId}-ch-email`}
                    checked={channels.includes("email")}
                    onCheckedChange={() => toggleChannel("email")}
                  />
                  <Label htmlFor={`${formId}-ch-email`}>邮件</Label>
                </div>
              </div>
            </div>
          </div>

          <DialogFooter>
            {isSubscribed && (
              <Button variant="ghost" onClick={handleUnsubscribe}>
                取消关注
              </Button>
            )}
            <Button onClick={handleSave} disabled={!allFields && selectedFields.length === 0}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
