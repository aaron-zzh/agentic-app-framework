/**
 * BrokerageRulePlanRatesCard——佣金规则下的套餐佣金比例管理
 *
 * 规则基础比例由 brokerage_rule 保存；此卡片管理各订阅套餐对该规则的比例覆盖，
 * 数据仍持久化到 brokerage_level_bonus，避免重复存储规则的业务匹配条件。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { BadgePercent, Plus, Trash2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { type SubscriptionPlanVO, useSubscriptionPlans } from "@/lib/api/rest/billing"
import {
  type CrudResource,
  useCrudCreate,
  useCrudDelete,
  useCrudList,
  useCrudUpdate
} from "@/lib/api/rest/crud"
import { notify } from "@/lib/notification"

interface BrokerageLevelBonus extends Record<string, unknown> {
  id: number
  ruleId: number
  planId: number
  level1Rate: number
  level2Rate: number
}

interface BrokerageRulePlanRatesCardProps {
  ruleId: string
  readOnly?: boolean
}

const levelBonusResource: CrudResource<BrokerageLevelBonus> = {
  apiPath: "/brokerage/level-bonuses"
}

function parseRate(value: string): number | null {
  const rate = Number(value)
  return Number.isFinite(rate) && rate >= 0 && rate <= 1 ? rate : null
}

function formatRate(rate: number): string {
  return String(rate)
}

function planLabel(plan: SubscriptionPlanVO): string {
  return `${plan.name}（${plan.code}）`
}

function BonusRateRow({
  bonus,
  planName,
  readOnly,
  onSave,
  onDelete
}: {
  bonus: BrokerageLevelBonus
  planName: string
  readOnly: boolean
  onSave: (bonus: BrokerageLevelBonus, level1Rate: string, level2Rate: string) => void
  onDelete: (id: number) => void
}) {
  const [level1Rate, setLevel1Rate] = useState(formatRate(bonus.level1Rate))
  const [level2Rate, setLevel2Rate] = useState(formatRate(bonus.level2Rate))

  return (
    <TableRow>
      <TableCell className="font-medium">{planName}</TableCell>
      <TableCell>
        <Input
          aria-label={`${planName}一级佣金比例`}
          className="w-24"
          value={level1Rate}
          onChange={(event) => setLevel1Rate(event.target.value)}
          disabled={readOnly}
          inputMode="decimal"
        />
      </TableCell>
      <TableCell>
        <Input
          aria-label={`${planName}二级佣金比例`}
          className="w-24"
          value={level2Rate}
          onChange={(event) => setLevel2Rate(event.target.value)}
          disabled={readOnly}
          inputMode="decimal"
        />
      </TableCell>
      {!readOnly ? (
        <TableCell className="text-right">
          <div className="flex justify-end gap-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onSave(bonus, level1Rate, level2Rate)}
            >
              保存
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => onDelete(bonus.id)}
              aria-label={`删除${planName}套餐比例`}
            >
              <Trash2 />
            </Button>
          </div>
        </TableCell>
      ) : null}
    </TableRow>
  )
}

/** 在佣金规则详情中维护套餐比例覆盖。 */
export function BrokerageRulePlanRatesCard({
  ruleId,
  readOnly = false
}: BrokerageRulePlanRatesCardProps) {
  const numericRuleId = Number(ruleId)
  const [open, setOpen] = useState(false)
  const [planId, setPlanId] = useState("")
  const [level1Rate, setLevel1Rate] = useState("")
  const [level2Rate, setLevel2Rate] = useState("")
  const { data: planPage, isLoading: plansLoading } = useSubscriptionPlans()
  const { data: bonusPage, isLoading: bonusesLoading } = useCrudList(levelBonusResource, {
    page: 1,
    pageSize: -1,
    ruleId: numericRuleId
  })
  const { mutate: create, isPending: creating } = useCrudCreate(levelBonusResource)
  const { mutate: update } = useCrudUpdate(levelBonusResource)
  const { mutate: remove } = useCrudDelete(levelBonusResource)
  const plans = planPage ?? []
  const bonuses = bonusPage?.list ?? []
  const planNames = new Map(plans.map((plan) => [Number(plan.id), planLabel(plan)]))

  function resetDraft() {
    setPlanId("")
    setLevel1Rate("")
    setLevel2Rate("")
  }

  function createBonus() {
    const selectedPlanId = Number(planId)
    const parsedLevel1Rate = parseRate(level1Rate)
    const parsedLevel2Rate = parseRate(level2Rate)
    if (!Number.isSafeInteger(selectedPlanId) || selectedPlanId <= 0) {
      notify.error("请选择套餐")
      return
    }
    if (parsedLevel1Rate == null || parsedLevel2Rate == null) {
      notify.error("佣金比例必须在 0 到 1 之间")
      return
    }
    if (bonuses.some((bonus) => bonus.planId === selectedPlanId)) {
      notify.error("该套餐已配置佣金比例")
      return
    }
    create(
      {
        ruleId: numericRuleId,
        planId: selectedPlanId,
        level1Rate: parsedLevel1Rate,
        level2Rate: parsedLevel2Rate
      },
      {
        onSuccess: () => {
          notify.success("套餐佣金比例已添加")
          resetDraft()
          setOpen(false)
        }
      }
    )
  }

  function saveBonus(bonus: BrokerageLevelBonus, nextLevel1Rate: string, nextLevel2Rate: string) {
    const parsedLevel1Rate = parseRate(nextLevel1Rate)
    const parsedLevel2Rate = parseRate(nextLevel2Rate)
    if (parsedLevel1Rate == null || parsedLevel2Rate == null) {
      notify.error("佣金比例必须在 0 到 1 之间")
      return
    }
    update(
      { id: bonus.id, data: { level1Rate: parsedLevel1Rate, level2Rate: parsedLevel2Rate } },
      { onSuccess: () => notify.success("套餐佣金比例已保存") }
    )
  }

  function deleteBonus(id: number) {
    remove({ id }, { onSuccess: () => notify.success("套餐佣金比例已删除") })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>套餐佣金比例</CardTitle>
        <CardDescription>
          未配置套餐时使用此规则的基础比例；配置后按分销员当前订阅套餐覆盖比例。
        </CardDescription>
        {!readOnly ? (
          <CardAction>
            <Button type="button" size="sm" onClick={() => setOpen(true)}>
              <Plus data-icon="inline-start" />
              添加套餐比例
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        {bonusesLoading ? (
          <p className="text-muted-foreground text-sm">正在加载套餐比例…</p>
        ) : bonuses.length === 0 ? (
          <Empty className="min-h-44">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <BadgePercent />
              </EmptyMedia>
              <EmptyTitle>尚未配置套餐佣金比例</EmptyTitle>
              <EmptyDescription>所有分销员将使用该规则的基础一级、二级佣金比例。</EmptyDescription>
            </EmptyHeader>
            {!readOnly ? (
              <EmptyContent>
                <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
                  添加套餐比例
                </Button>
              </EmptyContent>
            ) : null}
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>会员套餐</TableHead>
                <TableHead>一级比例</TableHead>
                <TableHead>二级比例</TableHead>
                {!readOnly ? <TableHead className="text-right">操作</TableHead> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {bonuses.map((bonus) => (
                <BonusRateRow
                  key={bonus.id}
                  bonus={bonus}
                  planName={planNames.get(bonus.planId) ?? `套餐 #${bonus.planId}`}
                  readOnly={readOnly}
                  onSave={saveBonus}
                  onDelete={deleteBonus}
                />
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>

      <Dialog open={open} onOpenChange={(nextOpen) => !creating && setOpen(nextOpen)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加套餐佣金比例</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="brokerage-plan">会员套餐</Label>
              <Select value={planId} onValueChange={(value) => setPlanId(value ?? "")}>
                <SelectTrigger id="brokerage-plan" className="w-full">
                  <SelectValue placeholder={plansLoading ? "正在加载套餐…" : "选择套餐"} />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {plans.map((plan) => (
                      <SelectItem key={plan.id} value={plan.id}>
                        {planLabel(plan)}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="brokerage-level1-rate">一级比例</Label>
                <Input
                  id="brokerage-level1-rate"
                  value={level1Rate}
                  onChange={(event) => setLevel1Rate(event.target.value)}
                  placeholder="如 0.1"
                  inputMode="decimal"
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="brokerage-level2-rate">二级比例</Label>
                <Input
                  id="brokerage-level2-rate"
                  value={level2Rate}
                  onChange={(event) => setLevel2Rate(event.target.value)}
                  placeholder="如 0.02"
                  inputMode="decimal"
                />
              </div>
            </div>
            <p className="text-muted-foreground text-xs">比例范围为 0 到 1，例如 0.1 表示 10%。</p>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={creating}
            >
              取消
            </Button>
            <Button type="button" onClick={createBonus} disabled={creating || plansLoading}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
