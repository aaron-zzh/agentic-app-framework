/**
 * 审批委托设置页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useId, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import type { DelegationCreateReq, DelegationScope, DelegationStatus } from "@/lib/api/delegation"
import { notify } from "@/lib/notification"
import {
  useCancelDelegation,
  useCreateDelegation,
  useDelegations
} from "@/lib/queries/use-delegations"

/** 状态标签颜色映射 */
const STATUS_VARIANT: Record<DelegationStatus, "default" | "secondary" | "destructive"> = {
  active: "default",
  expired: "secondary",
  cancelled: "destructive"
}

const STATUS_LABEL: Record<DelegationStatus, string> = {
  active: "生效中",
  expired: "已过期",
  cancelled: "已取消"
}

/** 可选流程列表（后续可从后端获取） */
const PROCESS_OPTIONS = [
  { key: "expense", label: "报销审批" },
  { key: "leave", label: "请假审批" },
  { key: "contract", label: "合同审批" },
  { key: "purchase", label: "采购审批" }
]

export default function DelegationSettingsPage() {
  const uid = useId()
  const { data: delegations, isLoading } = useDelegations()
  const { mutate: create, isPending: creating } = useCreateDelegation()
  const { mutate: cancel } = useCancelDelegation()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<DelegationCreateReq>({
    delegateTo: "",
    startTime: "",
    endTime: "",
    scope: "all",
    processKeys: []
  })

  const handleCreate = () => {
    if (!form.delegateTo || !form.startTime || !form.endTime) {
      notify.error("请填写完整信息")
      return
    }
    create(
      { ...form, processKeys: form.scope === "all" ? undefined : form.processKeys },
      {
        onSuccess: () => {
          notify.success("委托创建成功")
          setOpen(false)
          setForm({ delegateTo: "", startTime: "", endTime: "", scope: "all", processKeys: [] })
        },
        onError: () => notify.error("创建失败，请重试")
      }
    )
  }

  const handleCancel = (id: string) => {
    cancel(id, {
      onSuccess: () => notify.success("委托已取消"),
      onError: () => notify.error("取消失败")
    })
  }

  const toggleProcess = (key: string) => {
    setForm((prev) => {
      const keys = prev.processKeys ?? []
      return {
        ...prev,
        processKeys: keys.includes(key) ? keys.filter((k) => k !== key) : [...keys, key]
      }
    })
  }

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-semibold text-2xl">审批委托</h1>
          <p className="text-muted-foreground text-sm">将审批权限临时转交给指定代理人</p>
        </div>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger render={<Button>新建委托</Button>} />
          <DialogContent>
            <DialogHeader>
              <DialogTitle>新建审批委托</DialogTitle>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor={`${uid}-delegate`}>代理人</Label>
                <Input
                  id={`${uid}-delegate`}
                  placeholder="输入代理人用户名或 ID"
                  value={form.delegateTo}
                  onChange={(e) => setForm((prev) => ({ ...prev, delegateTo: e.target.value }))}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor={`${uid}-start`}>开始时间</Label>
                  <Input
                    id={`${uid}-start`}
                    type="date"
                    value={form.startTime}
                    onChange={(e) => setForm((prev) => ({ ...prev, startTime: e.target.value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`${uid}-end`}>结束时间</Label>
                  <Input
                    id={`${uid}-end`}
                    type="date"
                    value={form.endTime}
                    onChange={(e) => setForm((prev) => ({ ...prev, endTime: e.target.value }))}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>委托范围</Label>
                <Select
                  value={form.scope}
                  onValueChange={(v) =>
                    setForm((prev) => ({ ...prev, scope: v as DelegationScope }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">所有审批</SelectItem>
                    <SelectItem value="specific">指定流程</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {form.scope === "specific" && (
                <div className="space-y-2">
                  <Label>选择流程</Label>
                  <div className="flex flex-wrap gap-2">
                    {PROCESS_OPTIONS.map((p) => (
                      <Button
                        key={p.key}
                        type="button"
                        variant={(form.processKeys ?? []).includes(p.key) ? "default" : "outline"}
                        size="sm"
                        onClick={() => toggleProcess(p.key)}
                      >
                        {p.label}
                      </Button>
                    ))}
                  </div>
                </div>
              )}
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button onClick={handleCreate} disabled={creating}>
                {creating ? "创建中..." : "确认"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>委托记录</CardTitle>
          <CardDescription>当前及历史审批委托</CardDescription>
        </CardHeader>
        <CardContent>
          {!delegations?.length ? (
            <p className="py-8 text-center text-muted-foreground text-sm">暂无委托记录</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>代理人</TableHead>
                  <TableHead>生效时间</TableHead>
                  <TableHead>范围</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {delegations.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell>{d.delegateToName}</TableCell>
                    <TableCell className="text-sm">
                      {d.startTime} ~ {d.endTime}
                    </TableCell>
                    <TableCell>
                      {d.scope === "all" ? "所有审批" : (d.processKeys?.join("、") ?? "指定流程")}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[d.status]}>{STATUS_LABEL[d.status]}</Badge>
                    </TableCell>
                    <TableCell>
                      {d.status === "active" && (
                        <Button variant="ghost" size="sm" onClick={() => handleCancel(d.id)}>
                          取消
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
