"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { schemaUtils } from "@/components/form/schema-utils"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

const schema = z.object({
  name: schemaUtils.requiredText({ error: "请填写姓名", min: 2, max: 20 }),
  email: schemaUtils.email(),
  role: z.string().min(1, "请选择角色"),
  bio: z.string().max(100, "简介不超过 100 字"),
  notify: z.boolean()
})

type FormValues = z.infer<typeof schema>

export default function FormExamplePage() {
  const methods = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", email: "", role: "", bio: "", notify: false }
  })

  function onSubmit(data: FormValues) {
    toast.success("提交成功", {
      description: <pre className="mt-1 text-xs">{JSON.stringify(data, null, 2)}</pre>
    })
  }

  return (
    <div className="mx-auto max-w-lg p-6">
      <Card>
        <CardHeader>
          <CardTitle>表单示例</CardTitle>
          <CardDescription>使用 Form + Field + schemaUtils + zod 校验</CardDescription>
        </CardHeader>
        <CardContent>
          <Form methods={methods} onSubmit={onSubmit}>
            <Field.Text name="name" label="姓名" placeholder="请输入姓名" />
            <Field.Text name="email" label="邮箱" type="email" placeholder="name@example.com" />
            <Field.Select
              name="role"
              label="角色"
              description="选择你在系统中的角色"
              options={[
                { label: "管理员", value: "admin" },
                { label: "普通用户", value: "user" },
                { label: "访客", value: "guest" }
              ]}
            />
            <Field.Textarea name="bio" label="简介" description="最多 100 字" rows={3} />
            <Field.Switch name="notify" label="开启通知" description="接收系统消息推送" />
            <div className="flex gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => methods.reset()}>
                重置
              </Button>
              <Button type="submit">提交</Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
