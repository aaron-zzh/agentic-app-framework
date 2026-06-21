/**
 * 佣金规则配置页——规则列表 + 会员等级加成。
 * 手写表格与弹窗表单，复用底层通用 CRUD hooks（useCrudList/Create/Update/Delete）。
 * @author AaronZZH & Kiro
 */

"use client"

import { Pencil, Plus, Trash2 } from "lucide-react"
import { useId, useState } from "react"
import { toast } from "sonner"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
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
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH2 } from "@/components/ui/typography"
import {
  type CrudResource,
  useCrudCreate,
  useCrudDelete,
  useCrudList,
  useCrudUpdate
} from "@/lib/api/rest/crud"
import { paths } from "@/lib/constants/paths"

// ============================================================
// 类型定义（对齐后端 BrokerageRuleVO / BrokerageLevelBonusVO）
// ============================================================

interface BrokerageRule {
  [key: string]: unknown
  id: number
  name: string
  bizType: string
  bizTargetType: string | null
  bizTargetId: string | null
  // BigDecimal 经过 Jackson 默认序列化为 number；用 string|number 兼容字符串场景
  level1Rate: string | number
  level2Rate: string | number
  calcBase: string
  fixedAmount: number | null
  frozenDays: number
  priority: number
  status: string
}

interface BrokerageLevelBonus {
  [key: string]: unknown
  id: number
  ruleId: number
  planId: number
  level1Rate: string | number
  level2Rate: string | number
}

/**
 * 表单状态类型——与 BrokerageRule 字段对应但无索引签名。
 * 必须独立定义：BrokerageRule 含 `[key: string]: unknown` 索引签名，`Omit<T, "id">` 会把
 * 具体字段类型擦除为 unknown，导致表单字段访问全部失去类型推导。
 */
type RuleFormState = {
  name: string
  bizType: string
  bizTargetType: string | null
  bizTargetId: string | null
  level1Rate: number
  level2Rate: number
  calcBase: string
  fixedAmount: number | null
  frozenDays: number
  priority: number
  status: string
}

type BonusFormState = {
  ruleId: number
  planId: number
  level1Rate: number
  level2Rate: number
}

const ruleResource: CrudResource<BrokerageRule> = { apiPath: "/brokerage/rules" }
const bonusResource: CrudResource<BrokerageLevelBonus> = { apiPath: "/brokerage/level-bonuses" }

/** 业务类型枚举（对齐 BrokerageRecordBizTypeEnum） */
const BIZ_TYPE_OPTIONS = [
  { value: "ORDER", label: "订单" },
  { value: "SUBSCRIBE", label: "套餐订阅" },
  { value: "RECHARGE", label: "充值" },
  { value: "INVITE", label: "邀请绑定" }
]

const CALC_BASE_OPTIONS = [
  { value: "AMOUNT", label: "按金额比例" },
  { value: "FIXED", label: "固定金额" }
]

/** 比例显示：0.10 → "10%" */
function rateLabel(rate: string | number | null | undefined): string {
  if (rate == null) return "-"
  const n = typeof rate === "string" ? Number.parseFloat(rate) : rate
  if (Number.isNaN(n)) return "-"
  // 保留两位小数后去除尾随零
  const pct = (n * 100).toFixed(2).replace(/\.?0+$/, "")
  return `${pct}%`
}

function bizTypeLabel(value: string): string {
  return BIZ_TYPE_OPTIONS.find((o) => o.value === value)?.label ?? value
}

// ============================================================
// 页面入口
// ============================================================

export default function BrokerageRulesPage() {
  return (
    <PageContainer>
      <CustomBreadcrumbs
        links={[{ name: "首页", href: paths.workspace.root }, { name: "佣金规则配置" }]}
        className="mb-6"
      />

      <div className="flex flex-col gap-6">
        <RuleSection />
        <BonusSection />
      </div>
    </PageContainer>
  )
}

// ============================================================
// 佣金规则
// ============================================================

const EMPTY_RULE: RuleFormState = {
  name: "",
  bizType: "ORDER",
  bizTargetType: null,
  bizTargetId: null,
  level1Rate: 0.05,
  level2Rate: 0.01,
  calcBase: "AMOUNT",
  fixedAmount: null,
  frozenDays: 7,
  priority: 100,
  status: "ENABLED"
}

function RuleSection() {
  const formId = useId()
  const { data: page, isLoading } = useCrudList<BrokerageRule>(ruleResource, { pageSize: 100 })
  const rules = page?.list ?? []
  const { mutate: create, isPending: creating } = useCrudCreate<BrokerageRule>(ruleResource)
  const { mutate: update, isPending: updating } = useCrudUpdate<BrokerageRule>(ruleResource)
  const { mutate: remove } = useCrudDelete(ruleResource)

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<RuleFormState>(EMPTY_RULE)

  function openCreate() {
    setEditingId(null)
    setForm(EMPTY_RULE)
    setOpen(true)
  }

  function openEdit(rule: BrokerageRule) {
    setEditingId(rule.id)
    setForm({
      name: rule.name,
      bizType: rule.bizType,
      bizTargetType: rule.bizTargetType,
      bizTargetId: rule.bizTargetId,
      level1Rate: Number(rule.level1Rate),
      level2Rate: Number(rule.level2Rate),
      calcBase: rule.calcBase,
      fixedAmount: rule.fixedAmount,
      frozenDays: rule.frozenDays,
      priority: rule.priority,
      status: rule.status
    })
    setOpen(true)
  }

  function handleSubmit() {
    if (!form.name.trim() || !form.bizType.trim()) {
      toast.error("请填写规则名称和业务类型")
      return
    }
    const data = {
      ...form,
      bizTargetType: form.bizTargetType?.trim() || null,
      bizTargetId: form.bizTargetId?.trim() || null,
      level1Rate: Number(form.level1Rate),
      level2Rate: Number(form.level2Rate),
      fixedAmount: form.calcBase === "FIXED" ? form.fixedAmount : null
    }
    const handlers = {
      onSuccess: () => {
        toast.success(editingId != null ? "规则已更新" : "规则已创建")
        setOpen(false)
      },
      onError: (err: Error) => toast.error(err.message ?? "操作失败")
    }
    if (editingId != null) {
      update({ id: editingId, data }, handlers)
    } else {
      create(data, handlers)
    }
  }

  function handleDelete(rule: BrokerageRule) {
    if (!window.confirm(`确认删除规则「${rule.name}」？`)) return
    remove(
      { id: rule.id },
      {
        onSuccess: () => toast.success("规则已删除"),
        onError: (err) => toast.error(err.message ?? "删除失败")
      }
    )
  }

  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <TypographyH2 className="m-0">佣金规则</TypographyH2>
        <Button size="sm" onClick={openCreate}>
          <Plus className="mr-1 size-4" />
          新建规则
        </Button>
      </div>

      <Card className="overflow-hidden p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>业务类型</TableHead>
              <TableHead>目标</TableHead>
              <TableHead>一级比例</TableHead>
              <TableHead>二级比例</TableHead>
              <TableHead>计算基准</TableHead>
              <TableHead>冻结天数</TableHead>
              <TableHead>优先级</TableHead>
              <TableHead>状态</TableHead>
              <TableHead className="w-[120px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={10} className="text-center text-muted-foreground">
                  加载中…
                </TableCell>
              </TableRow>
            ) : rules.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} className="text-center text-muted-foreground">
                  暂无规则，点击右上角「新建规则」
                </TableCell>
              </TableRow>
            ) : (
              rules.map((rule) => (
                <TableRow key={rule.id}>
                  <TableCell className="font-medium">{rule.name}</TableCell>
                  <TableCell>{bizTypeLabel(rule.bizType)}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">
                    {rule.bizTargetType
                      ? `${rule.bizTargetType}${rule.bizTargetId ? `:${rule.bizTargetId}` : ""}`
                      : "全部"}
                  </TableCell>
                  <TableCell>{rateLabel(rule.level1Rate)}</TableCell>
                  <TableCell>{rateLabel(rule.level2Rate)}</TableCell>
                  <TableCell>
                    {rule.calcBase === "FIXED" ? `固定 ${rule.fixedAmount ?? 0} 分` : "按金额比例"}
                  </TableCell>
                  <TableCell>{rule.frozenDays}</TableCell>
                  <TableCell>{rule.priority}</TableCell>
                  <TableCell>
                    <Badge variant={rule.status === "ENABLED" ? "default" : "secondary"}>
                      {rule.status === "ENABLED" ? "启用" : "停用"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="编辑"
                        onClick={() => openEdit(rule)}
                      >
                        <Pencil className="size-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="删除"
                        className="text-destructive hover:text-destructive"
                        onClick={() => handleDelete(rule)}
                      >
                        <Trash2 className="size-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* 规则 Dialog */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>{editingId != null ? "编辑佣金规则" : "新建佣金规则"}</DialogTitle>
          </DialogHeader>

          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2 space-y-2">
              <Label htmlFor={`${formId}-name`}>规则名称</Label>
              <Input
                id={`${formId}-name`}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="如：套餐订阅默认佣金"
              />
            </div>

            <div className="space-y-2">
              <Label>业务类型</Label>
              <Select
                value={form.bizType}
                onValueChange={(v) => v && setForm({ ...form, bizType: v })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {BIZ_TYPE_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>计算基准</Label>
              <Select
                value={form.calcBase}
                onValueChange={(v) => v && setForm({ ...form, calcBase: v })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CALC_BASE_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-target-type`}>目标类型（可空）</Label>
              <Input
                id={`${formId}-target-type`}
                value={form.bizTargetType ?? ""}
                onChange={(e) => setForm({ ...form, bizTargetType: e.target.value })}
                placeholder="PRODUCT / PLAN / PACKAGE"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-target-id`}>目标 ID（可空）</Label>
              <Input
                id={`${formId}-target-id`}
                value={form.bizTargetId ?? ""}
                onChange={(e) => setForm({ ...form, bizTargetId: e.target.value })}
                placeholder="留空 = 该业务类型全部"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-l1`}>一级比例（小数，0.1 = 10%）</Label>
              <Input
                id={`${formId}-l1`}
                type="number"
                step="0.01"
                min="0"
                max="1"
                value={form.level1Rate}
                onChange={(e) =>
                  setForm({
                    ...form,
                    level1Rate: e.target.value === "" ? 0 : Number(e.target.value)
                  })
                }
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-l2`}>二级比例（小数）</Label>
              <Input
                id={`${formId}-l2`}
                type="number"
                step="0.01"
                min="0"
                max="1"
                value={form.level2Rate}
                onChange={(e) =>
                  setForm({
                    ...form,
                    level2Rate: e.target.value === "" ? 0 : Number(e.target.value)
                  })
                }
              />
            </div>

            {form.calcBase === "FIXED" && (
              <div className="space-y-2">
                <Label htmlFor={`${formId}-fixed`}>固定金额（分）</Label>
                <Input
                  id={`${formId}-fixed`}
                  type="number"
                  min="0"
                  value={form.fixedAmount ?? 0}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      fixedAmount: e.target.value === "" ? null : Number(e.target.value)
                    })
                  }
                />
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor={`${formId}-frozen`}>冻结天数</Label>
              <Input
                id={`${formId}-frozen`}
                type="number"
                min="0"
                value={form.frozenDays}
                onChange={(e) =>
                  setForm({
                    ...form,
                    frozenDays: e.target.value === "" ? 0 : Number(e.target.value)
                  })
                }
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-priority`}>优先级（数小为先）</Label>
              <Input
                id={`${formId}-priority`}
                type="number"
                value={form.priority}
                onChange={(e) =>
                  setForm({
                    ...form,
                    priority: e.target.value === "" ? 0 : Number(e.target.value)
                  })
                }
              />
            </div>

            <div className="col-span-2 flex items-center gap-2">
              <Switch
                id={`${formId}-status`}
                checked={form.status === "ENABLED"}
                onCheckedChange={(v) => setForm({ ...form, status: v ? "ENABLED" : "DISABLED" })}
              />
              <Label htmlFor={`${formId}-status`}>启用</Label>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubmit} disabled={creating || updating}>
              {editingId != null ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

// ============================================================
// 等级加成
// ============================================================

const EMPTY_BONUS: BonusFormState = {
  ruleId: 0,
  planId: 0,
  level1Rate: 0.1,
  level2Rate: 0.02
}

function BonusSection() {
  const formId = useId()
  const { data: rulesPage } = useCrudList<BrokerageRule>(ruleResource, { pageSize: 100 })
  const rules = rulesPage?.list ?? []
  const ruleNameMap = new Map(rules.map((r) => [r.id, r.name]))

  const { data: page, isLoading } = useCrudList<BrokerageLevelBonus>(bonusResource, {
    pageSize: 100
  })
  const bonuses = page?.list ?? []
  const { mutate: create, isPending: creating } = useCrudCreate<BrokerageLevelBonus>(bonusResource)
  const { mutate: update, isPending: updating } = useCrudUpdate<BrokerageLevelBonus>(bonusResource)
  const { mutate: remove } = useCrudDelete(bonusResource)

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<BonusFormState>(EMPTY_BONUS)

  function openCreate() {
    setEditingId(null)
    setForm({ ...EMPTY_BONUS, ruleId: rules[0]?.id ?? 0 })
    setOpen(true)
  }

  function openEdit(bonus: BrokerageLevelBonus) {
    setEditingId(bonus.id)
    setForm({
      ruleId: bonus.ruleId,
      planId: bonus.planId,
      level1Rate: Number(bonus.level1Rate),
      level2Rate: Number(bonus.level2Rate)
    })
    setOpen(true)
  }

  function handleSubmit() {
    if (!form.ruleId || !form.planId) {
      toast.error("请选择规则并填写套餐 ID")
      return
    }
    const data = {
      ruleId: Number(form.ruleId),
      planId: Number(form.planId),
      level1Rate: Number(form.level1Rate),
      level2Rate: Number(form.level2Rate)
    }
    const handlers = {
      onSuccess: () => {
        toast.success(editingId != null ? "加成已更新" : "加成已创建")
        setOpen(false)
      },
      onError: (err: Error) => toast.error(err.message ?? "操作失败")
    }
    if (editingId != null) {
      update({ id: editingId, data }, handlers)
    } else {
      create(data, handlers)
    }
  }

  function handleDelete(bonus: BrokerageLevelBonus) {
    if (!window.confirm("确认删除该等级加成？")) return
    remove(
      { id: bonus.id },
      {
        onSuccess: () => toast.success("已删除"),
        onError: (err) => toast.error(err.message ?? "删除失败")
      }
    )
  }

  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <TypographyH2 className="m-0">会员等级加成</TypographyH2>
        <Button size="sm" onClick={openCreate} disabled={rules.length === 0}>
          <Plus className="mr-1 size-4" />
          新建加成
        </Button>
      </div>
      <p className="mb-2 text-muted-foreground text-xs">
        当分销员订阅了指定套餐时，对应规则的佣金比例使用本表覆盖；未匹配则使用规则基础比例。
      </p>

      <Card className="overflow-hidden p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>规则</TableHead>
              <TableHead>套餐 ID</TableHead>
              <TableHead>一级比例</TableHead>
              <TableHead>二级比例</TableHead>
              <TableHead className="w-[120px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  加载中…
                </TableCell>
              </TableRow>
            ) : bonuses.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  {rules.length === 0 ? "请先创建至少一条佣金规则" : "暂无加成配置"}
                </TableCell>
              </TableRow>
            ) : (
              bonuses.map((bonus) => (
                <TableRow key={bonus.id}>
                  <TableCell>{ruleNameMap.get(bonus.ruleId) ?? `#${bonus.ruleId}`}</TableCell>
                  <TableCell>{bonus.planId}</TableCell>
                  <TableCell>{rateLabel(bonus.level1Rate)}</TableCell>
                  <TableCell>{rateLabel(bonus.level2Rate)}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="编辑"
                        onClick={() => openEdit(bonus)}
                      >
                        <Pencil className="size-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="删除"
                        className="text-destructive hover:text-destructive"
                        onClick={() => handleDelete(bonus)}
                      >
                        <Trash2 className="size-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* 加成 Dialog */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{editingId != null ? "编辑等级加成" : "新建等级加成"}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>关联规则</Label>
              <Select
                value={String(form.ruleId)}
                onValueChange={(v) => v && setForm({ ...form, ruleId: Number(v) })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择规则" />
                </SelectTrigger>
                <SelectContent>
                  {rules.map((r) => (
                    <SelectItem key={r.id} value={String(r.id)}>
                      {r.name}（{bizTypeLabel(r.bizType)}）
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor={`${formId}-plan`}>套餐 ID</Label>
              <Input
                id={`${formId}-plan`}
                type="number"
                min="0"
                value={form.planId}
                onChange={(e) =>
                  setForm({
                    ...form,
                    planId: e.target.value === "" ? 0 : Number(e.target.value)
                  })
                }
              />
              <p className="text-muted-foreground text-xs">
                billing_subscription_plan.id（套餐管理页可查看）
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor={`${formId}-l1`}>一级比例</Label>
                <Input
                  id={`${formId}-l1`}
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                  value={form.level1Rate}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      level1Rate: e.target.value === "" ? 0 : Number(e.target.value)
                    })
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor={`${formId}-l2`}>二级比例</Label>
                <Input
                  id={`${formId}-l2`}
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                  value={form.level2Rate}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      level2Rate: e.target.value === "" ? 0 : Number(e.target.value)
                    })
                  }
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubmit} disabled={creating || updating}>
              {editingId != null ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
