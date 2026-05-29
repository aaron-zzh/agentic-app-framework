/**
 * 行级数据权限管理页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useId, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
import { TypographyH1 } from "@/components/ui/typography"
import type {
  ConditionOperator,
  DataAccessRule,
  DataAccessRuleInput,
  RuleEffect
} from "@/lib/api/data-access"
import {
  useCreateDataAccessRule,
  useDataAccessRules,
  useDeleteDataAccessRule,
  useUpdateDataAccessRule
} from "@/lib/queries/use-data-access-rules"

/** 操作符显示标签 */
const OPERATOR_LABELS: Record<ConditionOperator, string> = {
  eq: "等于",
  ne: "不等于",
  gt: "大于",
  lt: "小于",
  in: "包含"
}

/** 效果显示标签 */
const EFFECT_LABELS: Record<RuleEffect, string> = {
  filter: "过滤",
  deny: "拒绝"
}

/** 表单初始值 */
const EMPTY_FORM: DataAccessRuleInput = {
  entitySlug: "",
  roles: [],
  condition: { field: "", operator: "eq", value: "" },
  effect: "filter"
}

export default function DataAccessPage() {
  const { data: rules = [] } = useDataAccessRules()
  const { mutate: create, isPending: creating } = useCreateDataAccessRule()
  const { mutate: update, isPending: updating } = useUpdateDataAccessRule()
  const { mutate: remove } = useDeleteDataAccessRule()

  const formId = useId()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<DataAccessRuleInput>(EMPTY_FORM)
  const [rolesInput, setRolesInput] = useState("")

  /** 按实体分组 */
  const grouped = rules.reduce<Record<string, DataAccessRule[]>>((acc, rule) => {
    const key = rule.entitySlug
    if (!acc[key]) acc[key] = []
    acc[key].push(rule)
    return acc
  }, {})

  function openCreate() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setRolesInput("")
    setDialogOpen(true)
  }

  function openEdit(rule: DataAccessRule) {
    setEditingId(rule.id)
    setForm({
      entitySlug: rule.entitySlug,
      roles: rule.roles,
      condition: { ...rule.condition },
      effect: rule.effect
    })
    setRolesInput(rule.roles.join(", "))
    setDialogOpen(true)
  }

  function handleSubmit() {
    const data: DataAccessRuleInput = {
      ...form,
      roles: rolesInput
        .split(",")
        .map((r) => r.trim())
        .filter(Boolean)
    }
    if (editingId) {
      update({ id: editingId, data }, { onSuccess: () => setDialogOpen(false) })
    } else {
      create(data, { onSuccess: () => setDialogOpen(false) })
    }
  }

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1 className="text-2xl">数据权限规则</TypographyH1>
        <Button onClick={openCreate}>新建规则</Button>
      </div>

      {/* 管理员预览占位 */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="font-medium text-sm">角色预览（开发中）</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm">
            以指定角色身份预览数据权限效果，功能开发中...
          </p>
        </CardContent>
      </Card>

      {/* 按实体分组展示规则 */}
      {Object.keys(grouped).length === 0 ? (
        <Empty className="py-12">
          <EmptyHeader>
            <EmptyTitle>暂无规则</EmptyTitle>
            <EmptyDescription>点击"新建规则"添加数据权限规则</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="space-y-4">
          {Object.entries(grouped).map(([entity, entityRules]) => (
            <Card key={entity}>
              <CardHeader>
                <CardTitle className="text-base">{entity}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {entityRules.map((rule) => (
                  <div
                    key={rule.id}
                    className="flex items-center justify-between rounded-md border px-3 py-2"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-sm">
                        {rule.condition.field} {OPERATOR_LABELS[rule.condition.operator]}{" "}
                        {rule.condition.value}
                      </span>
                      {rule.roles.map((role) => (
                        <Badge key={role} variant="outline">
                          {role}
                        </Badge>
                      ))}
                      <Badge variant={rule.effect === "deny" ? "destructive" : "secondary"}>
                        {EFFECT_LABELS[rule.effect]}
                      </Badge>
                    </div>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(rule)}>
                        编辑
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => remove(rule.id)}>
                        删除
                      </Button>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* 新建/编辑 Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingId ? "编辑规则" : "新建规则"}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <Label htmlFor={`${formId}-entity`}>实体</Label>
              <Input
                id={`${formId}-entity`}
                placeholder="如 document、order"
                value={form.entitySlug}
                onChange={(e) => setForm({ ...form, entitySlug: e.target.value })}
              />
            </div>

            <div>
              <Label htmlFor={`${formId}-field`}>字段</Label>
              <Input
                id={`${formId}-field`}
                placeholder="如 created_by、department_id"
                value={form.condition.field}
                onChange={(e) =>
                  setForm({ ...form, condition: { ...form.condition, field: e.target.value } })
                }
              />
            </div>

            <div>
              <Label>操作符</Label>
              <Select
                value={form.condition.operator}
                onValueChange={(v) =>
                  setForm({
                    ...form,
                    condition: { ...form.condition, operator: v as ConditionOperator }
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="eq">等于 (eq)</SelectItem>
                  <SelectItem value="ne">不等于 (ne)</SelectItem>
                  <SelectItem value="gt">大于 (gt)</SelectItem>
                  <SelectItem value="lt">小于 (lt)</SelectItem>
                  <SelectItem value="in">包含 (in)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor={`${formId}-value`}>值</Label>
              <Input
                id={`${formId}-value`}
                placeholder="如 $user.id、$user.departments"
                value={form.condition.value}
                onChange={(e) =>
                  setForm({ ...form, condition: { ...form.condition, value: e.target.value } })
                }
              />
            </div>

            <div>
              <Label htmlFor={`${formId}-roles`}>角色（逗号分隔）</Label>
              <Input
                id={`${formId}-roles`}
                placeholder="如 member, manager"
                value={rolesInput}
                onChange={(e) => setRolesInput(e.target.value)}
              />
            </div>

            <div>
              <Label>效果</Label>
              <Select
                value={form.effect}
                onValueChange={(v) => setForm({ ...form, effect: v as RuleEffect })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="filter">过滤（自动过滤不可见数据）</SelectItem>
                  <SelectItem value="deny">拒绝（拒绝访问）</SelectItem>
                </SelectContent>
              </Select>
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
    </PageContainer>
  )
}
