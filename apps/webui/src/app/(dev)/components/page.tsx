"use client"

/**
 * 组件预览页——用于检查 shadcn 组件样式、主题、交互是否正常
 * 路由：/dev/components
 */

import { useId, useState } from "react"
import { useForm } from "react-hook-form"
import { PageContainer } from "@/components/common/PageContainer"
import { UnsavedGuardDialog } from "@/components/common/UnsavedGuardDialog"
import { Wizard } from "@/components/common/Wizard"
import { FieldMoney, FieldQuantity } from "@/components/form/field-money"
import { FieldSignature } from "@/components/form/field-signature"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { Subtable } from "@/components/form/subtable"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { FormView } from "@/features/entity-engine/components/views/FormView"
import { taskEntity, userEntity } from "@/features/entity-engine/entities"
import type { DataFieldDef } from "@/features/entity-engine/types"

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h2 className="font-semibold text-muted-foreground text-sm uppercase tracking-wide">
        {title}
      </h2>
      <div className="flex flex-wrap items-start gap-3">{children}</div>
      <Separator />
    </div>
  )
}

export default function ComponentsPage() {
  const [checked, setChecked] = useState(false)
  const [switched, setSwitched] = useState(false)
  const [selectVal, setSelectVal] = useState("")
  const [progress, setProgress] = useState(40)
  const uid = useId()

  const methods = useForm({
    defaultValues: { name: "", bio: "", role: "", age: 0, agree: false, enabled: false, date: "" }
  })

  return (
    <PageContainer maxWidth="md">
      <h1 className="font-bold text-2xl">组件预览</h1>

      {/* Button */}
      <Section title="Button">
        <Button>默认</Button>
        <Button variant="secondary">次要</Button>
        <Button variant="outline">描边</Button>
        <Button variant="ghost">幽灵</Button>
        <Button variant="destructive">危险</Button>
        <Button disabled>禁用</Button>
      </Section>

      {/* Badge */}
      <Section title="Badge">
        <Badge>默认</Badge>
        <Badge variant="secondary">次要</Badge>
        <Badge variant="outline">描边</Badge>
        <Badge variant="destructive">危险</Badge>
      </Section>

      {/* Input */}
      <Section title="Input / Label">
        <div className="flex w-48 flex-col gap-1.5">
          <Label htmlFor={`${uid}-demo`}>标签</Label>
          <Input id={`${uid}-demo`} placeholder="请输入..." />
        </div>
        <div className="flex w-48 flex-col gap-1.5">
          <Label htmlFor={`${uid}-demo-err`}>错误状态</Label>
          <Input id={`${uid}-demo-err`} aria-invalid placeholder="错误输入" />
        </div>
        <div className="flex w-48 flex-col gap-1.5">
          <Label>禁用</Label>
          <Input disabled placeholder="禁用输入" />
        </div>
      </Section>

      {/* Textarea */}
      <Section title="Textarea">
        <Textarea className="w-64" placeholder="多行文本..." />
        <Textarea className="w-64" disabled placeholder="禁用" />
      </Section>

      {/* Select */}
      <Section title="Select">
        <Select value={selectVal} onValueChange={(v) => setSelectVal(v ?? "")}>
          <SelectTrigger className="w-40">
            <SelectValue placeholder="请选择" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="draft">草稿</SelectItem>
              <SelectItem value="published">已发布</SelectItem>
              <SelectItem value="archived">已归档</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
        <span className="text-muted-foreground text-sm">当前值：{selectVal || "—"}</span>
      </Section>

      {/* Checkbox */}
      <Section title="Checkbox">
        <div className="flex items-center gap-2">
          <Checkbox
            id={`${uid}-cb1`}
            checked={checked}
            onCheckedChange={(v) => setChecked(v === true)}
          />
          <Label htmlFor={`${uid}-cb1`}>复选框（{checked ? "✓" : "○"}）</Label>
        </div>
        <div className="flex items-center gap-2">
          <Checkbox id={`${uid}-cb2`} disabled />
          <Label htmlFor={`${uid}-cb2`}>禁用</Label>
        </div>
      </Section>

      {/* Switch */}
      <Section title="Switch">
        <div className="flex items-center gap-2">
          <Switch id={`${uid}-sw1`} checked={switched} onCheckedChange={setSwitched} />
          <Label htmlFor={`${uid}-sw1`}>开关（{switched ? "开" : "关"}）</Label>
        </div>
        <div className="flex items-center gap-2">
          <Switch id={`${uid}-sw2`} disabled />
          <Label htmlFor={`${uid}-sw2`}>禁用</Label>
        </div>
      </Section>

      {/* Tooltip */}
      <Section title="Tooltip">
        <Tooltip>
          <TooltipTrigger>悬停查看提示</TooltipTrigger>
          <TooltipContent>这是一个提示</TooltipContent>
        </Tooltip>
      </Section>

      {/* Progress */}
      <Section title="Progress">
        <div className="w-64 space-y-2">
          <Progress value={progress} />
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => setProgress(Math.max(0, progress - 10))}
            >
              -10
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setProgress(Math.min(100, progress + 10))}
            >
              +10
            </Button>
            <span className="text-muted-foreground text-sm">{progress}%</span>
          </div>
        </div>
      </Section>

      {/* Skeleton */}
      <Section title="Skeleton">
        <div className="space-y-2">
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-8 w-64" />
        </div>
      </Section>

      {/* Tabs */}
      <Section title="Tabs">
        <Tabs defaultValue="tab1" className="w-80">
          <TabsList>
            <TabsTrigger value="tab1">标签一</TabsTrigger>
            <TabsTrigger value="tab2">标签二</TabsTrigger>
            <TabsTrigger value="tab3" disabled>
              禁用
            </TabsTrigger>
          </TabsList>
          <TabsContent value="tab1" className="p-3 text-sm">
            标签一的内容
          </TabsContent>
          <TabsContent value="tab2" className="p-3 text-sm">
            标签二的内容
          </TabsContent>
        </Tabs>
      </Section>

      {/* Form 组件 */}
      <Section title="Form（RHF 封装）">
        <Form methods={methods} className="w-full max-w-sm space-y-4">
          <Field.Text name="name" label="姓名" placeholder="请输入姓名" />
          <Field.Number name="age" label="年龄" min={0} max={150} />
          <Field.Select
            name="role"
            label="角色"
            options={[
              { label: "管理员", value: "admin" },
              { label: "普通用户", value: "user" }
            ]}
          />
          <Field.Textarea name="bio" label="简介" rows={3} />
          <Field.Date name="date" label="日期" />
          <Field.Checkbox name="agree" label="同意协议" />
          <Field.Switch name="enabled" label="启用通知" />
          <Button type="submit">提交</Button>
        </Form>
      </Section>

      {/* FormView（EntityDef 驱动） */}
      <Section title="FormView（EntityDef 驱动）">
        <div className="w-full rounded-lg border">
          <FormView entity={userEntity} />
        </div>
      </Section>

      <Section title="FormView（任务实体）">
        <div className="w-full rounded-lg border">
          <FormView entity={taskEntity} />
        </div>
      </Section>

      <SubtableSection />
      <SignatureSection />
      <MoneySection />
      <WizardSection />
      <UnsavedGuardSection />
    </PageContainer>
  )
}

/** 子表明细行 */
function SubtableSection() {
  const fields: DataFieldDef[] = [
    { type: "text", name: "product", label: "商品名称" },
    { type: "number", name: "price", label: "单价" },
    { type: "number", name: "qty", label: "数量" }
  ]
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  return (
    <Section title="Subtable 子表明细">
      <div className="w-full">
        <Subtable
          fields={fields}
          value={rows}
          onChange={setRows}
          summaryFields={["price", "qty"]}
        />
      </div>
    </Section>
  )
}

/** 签名字段 */
function SignatureSection() {
  const [sig, setSig] = useState("")
  return (
    <Section title="Signature 手写签名">
      <div className="w-80">
        <FieldSignature name="sig" label="审批人签名" value={sig} onChange={setSig} />
        {sig && <p className="mt-2 text-muted-foreground text-xs">已签名（{sig.length} 字符）</p>}
      </div>
    </Section>
  )
}

/** 多币种/单位 */
function MoneySection() {
  const [money, setMoney] = useState({ value: 0, currency: "CNY" })
  const [qty, setQty] = useState({ value: 0, unit: "kg" })
  return (
    <Section title="Money / Quantity">
      <FieldMoney name="amount" label="金额" value={money} onChange={setMoney} />
      <FieldQuantity name="weight" label="重量" value={qty} onChange={setQty} />
    </Section>
  )
}

/** 向导弹窗 */
function WizardSection() {
  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<Record<string, unknown> | null>(null)
  return (
    <Section title="Wizard 向导弹窗">
      <Button onClick={() => setOpen(true)}>打开向导</Button>
      {result && <p className="text-muted-foreground text-sm">完成：{JSON.stringify(result)}</p>}
      <Wizard
        open={open}
        onClose={() => setOpen(false)}
        onComplete={(data) => {
          setResult(data)
          setOpen(false)
        }}
        title="三步向导示例"
        steps={[
          { label: "步骤一", content: <p className="text-sm">填写基本信息</p> },
          { label: "步骤二", content: <p className="text-sm">确认配置</p> },
          { label: "步骤三", content: <p className="text-sm">完成</p> }
        ]}
      />
    </Section>
  )
}

/** 离开确认对话框 */
function UnsavedGuardSection() {
  const [open, setOpen] = useState(false)
  return (
    <Section title="UnsavedGuardDialog 离开确认">
      <Button variant="outline" onClick={() => setOpen(true)}>
        模拟有未保存修改
      </Button>
      <UnsavedGuardDialog
        open={open}
        onDiscardAndLeave={() => {
          alert("放弃修改")
          setOpen(false)
        }}
        onCancel={() => setOpen(false)}
      />
    </Section>
  )
}
