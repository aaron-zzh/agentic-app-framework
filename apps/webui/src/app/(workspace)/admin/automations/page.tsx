/**
 * 自动化规则管理页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useId, useState } from "react"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { TypographyH1 } from "@/components/ui/typography"
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog"
import type {
  AutomationAction,
  AutomationCondition,
  AutomationRule,
  AutomationRuleInput,
  TriggerType
} from "@/lib/api/automation"
import {
  useAutomationLogs,
  useAutomationRules,
  useCreateAutomationRule,
  useDeleteAutomationRule,
  useTestAutomationRule,
  useToggleAutomationRule,
  useUpdateAutomationRule
} from "@/lib/queries/use-automations"

/** 触发器类型标签 */
const TRIGGER_LABELS: Record<TriggerType, string> = {
  on_create: "创建时",
  on_update: "更新时",
  field_change: "字段变更",
  schedule: "定时",
  delay: "延迟"
}

/** 操作摘要 */
function actionSummary(actions: AutomationAction[]): string {
  return actions.map((a) => a.type).join(" → ")
}

/** 表单初始值 */
const EMPTY_FORM: AutomationRuleInput = {
  name: "",
  entitySlug: "",
  trigger: { type: "on_create" },
  conditions: [],
  actions: []
}

export default function AutomationsPage() {
  const { data: rules = [] } = useAutomationRules()
  const { data: logs = [] } = useAutomationLogs()
  const { mutate: create, isPending: creating } = useCreateAutomationRule()
  const { mutate: update, isPending: updating } = useUpdateAutomationRule()
  const { mutate: remove } = useDeleteAutomationRule()
  const { mutate: toggle } = useToggleAutomationRule()
  const { mutate: testRun, isPending: testing } = useTestAutomationRule()

  const formId = useId()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<AutomationRuleInput>(EMPTY_FORM)
  const [conditionsJson, setConditionsJson] = useState("[]")
  const [actionsJson, setActionsJson] = useState("[]")
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

  function openCreate() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setConditionsJson("[]")
    setActionsJson("[]")
    setDialogOpen(true)
  }

  function openEdit(rule: AutomationRule) {
    setEditingId(rule.id)
    setForm({
      name: rule.name,
      entitySlug: rule.entitySlug,
      trigger: { ...rule.trigger },
      conditions: rule.conditions,
      actions: rule.actions
    })
    setConditionsJson(JSON.stringify(rule.conditions, null, 2))
    setActionsJson(JSON.stringify(rule.actions, null, 2))
    setDialogOpen(true)
  }

  function handleSubmit() {
    let conditions: AutomationCondition[] = []
    let actions: AutomationAction[] = []
    try {
      conditions = JSON.parse(conditionsJson)
    } catch {
      toast.error("条件 JSON 格式错误，请检查语法")
      return
    }
    try {
      actions = JSON.parse(actionsJson)
    } catch {
      toast.error("操作链 JSON 格式错误，请检查语法")
      return
    }
    const data: AutomationRuleInput = { ...form, conditions, actions }
    if (editingId) {
      update({ id: editingId, data }, { onSuccess: () => setDialogOpen(false) })
    } else {
      create(data, { onSuccess: () => setDialogOpen(false) })
    }
  }

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1 className="text-2xl">自动化规则</TypographyH1>
        <Button onClick={openCreate}>新建规则</Button>
      </div>

      <Tabs defaultValue="rules">
        <TabsList>
          <TabsTrigger value="rules">规则列表</TabsTrigger>
          <TabsTrigger value="logs">执行日志</TabsTrigger>
        </TabsList>

        {/* 规则列表 */}
        <TabsContent value="rules">
          {rules.length === 0 ? (
            <Empty className="py-12">
              <EmptyHeader>
                <EmptyTitle>暂无规则</EmptyTitle>
                <EmptyDescription>点击"新建规则"创建自动化规则</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="space-y-3">
              {rules.map((rule) => (
                <Card key={rule.id}>
                  <CardContent className="flex items-center justify-between py-4">
                    <div className="flex items-center gap-3">
                      <Switch
                        checked={rule.enabled}
                        onCheckedChange={(checked) => toggle({ id: rule.id, enabled: !!checked })}
                      />
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{rule.name}</span>
                          <Badge variant="outline">{rule.entitySlug}</Badge>
                          <Badge variant="secondary">{TRIGGER_LABELS[rule.trigger.type]}</Badge>
                        </div>
                        <p className="text-muted-foreground text-sm">
                          操作：{actionSummary(rule.actions) || "无"}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={testing}
                        onClick={() => testRun(rule.id)}
                      >
                        测试运行
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => openEdit(rule)}>
                        编辑
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setDeleteConfirmId(rule.id)}>
                        删除
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>

        {/* 执行日志 */}
        <TabsContent value="logs">
          {logs.length === 0 ? (
            <Empty className="py-12">
              <EmptyHeader>
                <EmptyTitle>暂无日志</EmptyTitle>
                <EmptyDescription>规则执行后将在此显示日志</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="space-y-2">
              {logs.map((log) => (
                <Card key={log.id}>
                  <CardContent className="flex items-center justify-between py-3">
                    <div className="flex items-center gap-2">
                      <Badge
                        variant={
                          log.status === "success"
                            ? "default"
                            : log.status === "failed"
                              ? "destructive"
                              : "secondary"
                        }
                      >
                        {log.status}
                      </Badge>
                      <span className="text-sm">{log.ruleName ?? log.ruleId}</span>
                      <Badge variant="outline">{TRIGGER_LABELS[log.triggerType]}</Badge>
                    </div>
                    <div className="flex items-center gap-2">
                      {log.errorMessage && (
                        <span className="text-destructive text-xs">{log.errorMessage}</span>
                      )}
                      <span className="text-muted-foreground text-xs">{log.executedAt}</span>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* 新建/编辑 Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingId ? "编辑规则" : "新建规则"}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <Label htmlFor={`${formId}-name`}>规则名称</Label>
              <Input
                id={`${formId}-name`}
                placeholder="如：逾期未处理自动提醒"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>

            <div>
              <Label htmlFor={`${formId}-entity`}>实体</Label>
              <Input
                id={`${formId}-entity`}
                placeholder="如 document、order"
                value={form.entitySlug}
                onChange={(e) => setForm({ ...form, entitySlug: e.target.value })}
              />
            </div>

            {/* 触发器 */}
            <div>
              <Label>触发器</Label>
              <Select
                value={form.trigger.type}
                onValueChange={(v) =>
                  setForm({
                    ...form,
                    trigger: { ...form.trigger, type: v as TriggerType }
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="on_create">创建时</SelectItem>
                  <SelectItem value="on_update">更新时</SelectItem>
                  <SelectItem value="field_change">字段变更</SelectItem>
                  <SelectItem value="schedule">定时（Cron）</SelectItem>
                  <SelectItem value="delay">延迟（N 天后）</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* 触发器额外参数 */}
            {form.trigger.type === "field_change" && (
              <div>
                <Label htmlFor={`${formId}-field`}>监听字段</Label>
                <Input
                  id={`${formId}-field`}
                  placeholder="如 status"
                  value={form.trigger.field ?? ""}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      trigger: { ...form.trigger, field: e.target.value }
                    })
                  }
                />
              </div>
            )}
            {form.trigger.type === "schedule" && (
              <div>
                <Label htmlFor={`${formId}-cron`}>Cron 表达式</Label>
                <Input
                  id={`${formId}-cron`}
                  placeholder="如 0 9 * * 1（每周一 9:00）"
                  value={form.trigger.cron ?? ""}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      trigger: { ...form.trigger, cron: e.target.value }
                    })
                  }
                />
              </div>
            )}
            {form.trigger.type === "delay" && (
              <div>
                <Label htmlFor={`${formId}-delay`}>延迟天数</Label>
                <Input
                  id={`${formId}-delay`}
                  type="number"
                  value={form.trigger.delayDays ?? 0}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      trigger: {
                        ...form.trigger,
                        delayDays: Number(e.target.value)
                      }
                    })
                  }
                />
              </div>
            )}

            {/* 条件 JSON */}
            <div>
              <Label htmlFor={`${formId}-conditions`}>条件（JSON）</Label>
              <Textarea
                id={`${formId}-conditions`}
                className="font-mono text-xs"
                rows={4}
                placeholder='[{"field":"status","operator":"eq","value":"pending"}]'
                value={conditionsJson}
                onChange={(e) => setConditionsJson(e.target.value)}
              />
            </div>

            {/* 操作 JSON */}
            <div>
              <Label htmlFor={`${formId}-actions`}>操作链（JSON）</Label>
              <Textarea
                id={`${formId}-actions`}
                className="font-mono text-xs"
                rows={4}
                placeholder='[{"type":"send_notification","config":{"message":"逾期提醒"}}]'
                value={actionsJson}
                onChange={(e) => setActionsJson(e.target.value)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubmit} disabled={creating || updating}>
              {editingId ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认 */}
      <AlertDialog open={!!deleteConfirmId} onOpenChange={(open) => { if (!open) setDeleteConfirmId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>删除后不可恢复，确定要删除此自动化规则吗？</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={() => { if (deleteConfirmId) { remove(deleteConfirmId); setDeleteConfirmId(null) } }}>
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </PageContainer>
  )
}
