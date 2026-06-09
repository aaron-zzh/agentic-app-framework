"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { ComponentBox, ComponentLayout } from "@/components/common/ComponentLayout"
import { UnsavedGuardDialog } from "@/components/common/UnsavedGuardDialog"
import { Wizard } from "@/components/common/Wizard"
import { FieldMoney, FieldQuantity } from "@/components/form/field-money"
import { FieldSignature } from "@/components/form/field-signature"
import { FieldTextarea } from "@/components/form/field-textarea"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { Subtable } from "@/components/form/subtable"
import { Button } from "@/components/ui/button"
import type { UploadFile } from "@/components/upload"
import { Upload, UploadAvatar } from "@/components/upload"
import { FormView } from "@/features/entity-engine/components/form/FormView"
import { taskEntity, userEntity } from "@/features/entity-engine/entities"
import type { DataFieldDef } from "@/features/entity-engine/types"

export default function FormPage() {
  const methods = useForm({
    defaultValues: { name: "", bio: "", role: "", age: 0, agree: false, enabled: false, date: "" }
  })

  return (
    <ComponentLayout
      heading="表单组件"
      description="基于 react-hook-form + zod 的表单控件体系。"
      links={[{ name: "react-hook-form", href: "https://react-hook-form.com/" }]}
      sectionData={[
        {
          name: "Field 控件（RHF 封装）",
          component: (
            <ComponentBox className="items-start justify-start">
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
            </ComponentBox>
          )
        },
        {
          name: "FormView（EntityDef 驱动）",
          description: "用户实体",
          component: (
            <ComponentBox className="items-start justify-start">
              <div className="w-full rounded-lg border">
                <FormView entity={userEntity} />
              </div>
            </ComponentBox>
          )
        },
        {
          name: "FormView（任务实体）",
          component: (
            <ComponentBox className="items-start justify-start">
              <div className="w-full rounded-lg border">
                <FormView entity={taskEntity} />
              </div>
            </ComponentBox>
          )
        },
        { name: "Subtable 子表明细", component: <SubtableDemo /> },
        { name: "Signature 手写签名", component: <SignatureDemo /> },
        { name: "Money / Quantity", component: <MoneyDemo /> },
        { name: "Image Upload", component: <UploadDemo /> },
        { name: "Textarea 升级富文本", component: <TextareaUpgradeDemo /> },
        { name: "Wizard 向导弹窗", component: <WizardDemo /> },
        { name: "UnsavedGuardDialog 离开确认", component: <UnsavedGuardDemo /> }
      ]}
    />
  )
}

function SubtableDemo() {
  const fields: DataFieldDef[] = [
    { type: "text", name: "product", label: "商品名称" },
    { type: "number", name: "price", label: "单价" },
    { type: "number", name: "qty", label: "数量" }
  ]
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  return (
    <ComponentBox className="items-start justify-start">
      <div className="w-full">
        <Subtable
          fields={fields}
          value={rows}
          onChange={setRows}
          summaryFields={["price", "qty"]}
        />
      </div>
    </ComponentBox>
  )
}

function SignatureDemo() {
  const [sig, setSig] = useState("")
  return (
    <ComponentBox>
      <div className="w-80">
        <FieldSignature name="sig" label="审批人签名" value={sig} onChange={setSig} />
        {sig && <p className="mt-2 text-muted-foreground text-xs">已签名（{sig.length} 字符）</p>}
      </div>
    </ComponentBox>
  )
}

function MoneyDemo() {
  const [money, setMoney] = useState({ value: 0, currency: "CNY" })
  const [qty, setQty] = useState({ value: 0, unit: "kg" })
  return (
    <ComponentBox>
      <FieldMoney name="amount" label="金额" value={money} onChange={setMoney} />
      <FieldQuantity name="weight" label="重量" value={qty} onChange={setQty} />
    </ComponentBox>
  )
}

function UploadDemo() {
  const [files, setFiles] = useState<UploadFile[]>([])
  const [avatar, setAvatar] = useState<string>()
  const methods = useForm({ defaultValues: { cover: "", gallery: [] as string[] } })
  return (
    <ComponentBox className="flex-col items-start gap-6">
      <div className="w-full space-y-1">
        <p className="font-medium text-sm">基础上传（自动压缩 + 进度条）</p>
        <Upload
          accept="image/*"
          multiple
          value={files}
          onChange={setFiles}
          onRemove={(i) => setFiles((prev) => prev.filter((_, idx) => idx !== i))}
          imageOptions={{ maxWidth: 1280, quality: 0.75 }}
        />
      </div>
      <div className="space-y-1">
        <p className="font-medium text-sm">头像上传（圆形裁剪）</p>
        <UploadAvatar
          value={avatar}
          onChange={setAvatar}
          imageOptions={{ maxWidth: 512, maxHeight: 512 }}
        />
      </div>
      <div className="w-full space-y-1">
        <p className="font-medium text-sm">Field.Upload（表单绑定）</p>
        <Form methods={methods} className="max-w-sm space-y-3">
          <Field.Upload name="cover" label="封面图（单张）" accept="image/*" maxSize="5%" />
          <Field.Upload name="gallery" label="图片集（多张）" accept="image/*" multiple />
          <Button type="submit" size="sm">
            提交
          </Button>
        </Form>
      </div>
    </ComponentBox>
  )
}

function TextareaUpgradeDemo() {
  const methods = useForm({ defaultValues: { content: "", notes: "" } })
  return (
    <ComponentBox className="items-start justify-start">
      <Form methods={methods} className="w-full max-w-lg space-y-4">
        <FieldTextarea name="content" label="内容（可升级）" rows={4} allowRichText />
        <FieldTextarea name="notes" label="备注（禁止升级）" rows={3} allowRichText={false} />
      </Form>
    </ComponentBox>
  )
}

function WizardDemo() {
  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<Record<string, unknown> | null>(null)
  return (
    <ComponentBox>
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
    </ComponentBox>
  )
}

function UnsavedGuardDemo() {
  const [open, setOpen] = useState(false)
  return (
    <ComponentBox>
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
    </ComponentBox>
  )
}
