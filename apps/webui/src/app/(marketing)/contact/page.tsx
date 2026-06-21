/**
 * 联系我们页——访客留言入口（写入 ops_guest_lead, channel=CONTACT）
 * 参考 arts contact-us 设计：左侧联系方式 + 右侧留言表单
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Globe, Mail, MessageCircle, Phone } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { schemaUtils } from "@/components/form/schema-utils"
import { Button } from "@/components/ui/button"
import { leadApi } from "@/lib/api/rest/lead/lead"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"

const schema = z.object({
  name: z.string().max(100).optional(),
  email: z.string().email("邮箱格式无效").optional().or(z.literal("")),
  phone: z.string().max(50).optional(),
  subject: z.string().max(200).optional(),
  content: schemaUtils.requiredText({ error: "请描述您的需求", min: 5, max: 2000 })
})

type FormValues = z.infer<typeof schema>

const CONTACT_CHANNELS = [
  { icon: Mail, label: "邮箱", value: "service@xuejiai.com" },
  { icon: MessageCircle, label: "微信", value: "Aaron-ZZH" },
  { icon: Phone, label: "电话", value: "工作日 09:00-18:00" },
  { icon: Globe, label: "Github", value: "github.com/aaron-zzh/agentic-app-framework" }
]

export default function ContactPage() {
  const [submitted, setSubmitted] = useState(false)
  const methods = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", email: "", phone: "", subject: "", content: "" }
  })

  const onSubmit = async (data: FormValues) => {
    try {
      await leadApi.create({
        anonymousId: getOrCreateAnonymousId(),
        channel: "CONTACT",
        name: data.name || undefined,
        email: data.email || undefined,
        phone: data.phone || undefined,
        subject: data.subject || undefined,
        content: data.content
      })
      setSubmitted(true)
      toast.success("已提交，我们会尽快与您联系")
      methods.reset()
    } catch {
      toast.error("提交失败，请稍后再试")
    }
  }

  return (
    <main className="mx-auto w-full max-w-(--layout-marketing-max-width) px-6 py-16 md:py-24">
      <header className="text-center">
        <h1 className="font-bold text-3xl tracking-tight md:text-4xl">联系我们</h1>
        <p className="mx-auto mt-3 max-w-xl text-muted-foreground text-sm md:text-base">
          有需求、合作或问题？留下您的信息，我们会尽快与您联系。
        </p>
      </header>

      <div className="mt-12 grid gap-10 md:grid-cols-2">
        {/* 左：联系方式 */}
        <section className="space-y-4">
          <h2 className="font-semibold text-xl">联系方式</h2>
          <p className="text-muted-foreground text-sm">
            您可以通过以下方式直接联系我们，或使用右侧表单留言。
          </p>
          <ul className="mt-6 space-y-3">
            {CONTACT_CHANNELS.map(({ icon: Icon, label, value }) => (
              <li key={label} className="flex items-start gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
                  <Icon className="size-4" />
                </span>
                <div>
                  <p className="font-medium text-sm">{label}</p>
                  <p className="text-muted-foreground text-sm">{value}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>

        {/* 右：表单 */}
        <section className="rounded-xl border bg-card p-6 shadow-sm md:p-8">
          {submitted ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
              <h3 className="font-semibold text-xl">感谢您的留言！</h3>
              <p className="text-muted-foreground text-sm">
                我们已收到您的信息，会在 1-3 个工作日内回复。
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSubmitted(false)}
                className="mt-2"
              >
                再留一条
              </Button>
            </div>
          ) : (
            <Form methods={methods} onSubmit={onSubmit}>
              <div className="grid gap-3 md:grid-cols-2">
                <Field.Text name="name" label="姓名" placeholder="选填" />
                <Field.Text name="email" label="邮箱" type="email" placeholder="选填" />
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Field.Text name="phone" label="电话" placeholder="选填" />
                <Field.Text name="subject" label="主题" placeholder="选填" />
              </div>
              <Field.Textarea
                name="content"
                label="留言"
                placeholder="请描述您的需求或问题（5-2000 字）"
                rows={6}
                allowRichText={false}
              />
              <Button
                type="submit"
                disabled={methods.formState.isSubmitting}
                className="w-full md:w-auto"
              >
                {methods.formState.isSubmitting ? "提交中..." : "提交留言"}
              </Button>
            </Form>
          )}
        </section>
      </div>
    </main>
  )
}
