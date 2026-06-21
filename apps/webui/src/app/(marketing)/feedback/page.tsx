/**
 * 用户反馈页——访客提交反馈（写入 ops_guest_lead, channel=FEEDBACK）
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { schemaUtils } from "@/components/form/schema-utils"
import { Button } from "@/components/ui/button"
import { leadApi } from "@/lib/api/rest/lead/lead"
import { APP } from "@/lib/config"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"

/** 反馈类型——提交时存到 lead.subject 字段，便于管理端筛选 */
const FEEDBACK_TYPES = [
  { label: "Bug 报告", value: "BUG" },
  { label: "功能建议", value: "SUGGESTION" },
  { label: "用户体验", value: "UX" },
  { label: "其他", value: "OTHER" }
] as const

const schema = z.object({
  subject: z.string().min(1, "请选择反馈类型"),
  email: z.string().email("邮箱格式无效").optional().or(z.literal("")),
  content: schemaUtils.requiredText({ error: "请详细描述", min: 5, max: 2000 })
})

type FormValues = z.infer<typeof schema>

export default function FeedbackPage() {
  const [submitted, setSubmitted] = useState(false)
  const methods = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { subject: "SUGGESTION", email: "", content: "" }
  })

  const onSubmit = async (data: FormValues) => {
    try {
      await leadApi.create({
        anonymousId: getOrCreateAnonymousId(),
        channel: "FEEDBACK",
        subject: data.subject,
        email: data.email || undefined,
        content: data.content
      })
      setSubmitted(true)
      toast.success("感谢您的反馈！")
      methods.reset({ subject: "SUGGESTION", email: "", content: "" })
    } catch {
      toast.error("提交失败，请稍后再试")
    }
  }

  return (
    <main className="mx-auto w-full max-w-3xl px-6 py-16 md:py-24">
      <header className="text-center">
        <h1 className="font-bold text-3xl tracking-tight md:text-4xl">用户反馈</h1>
        <p className="mx-auto mt-3 max-w-xl text-muted-foreground text-sm md:text-base">
          您的每一条反馈都会让 {APP.name} 变得更好。Bug、建议、体验问题都欢迎告诉我们。
        </p>
      </header>

      <section className="mt-12 rounded-xl border bg-card p-6 shadow-sm md:p-8">
        {submitted ? (
          <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
            <h3 className="font-semibold text-xl">已收到您的反馈！</h3>
            <p className="text-muted-foreground text-sm">
              如留下了邮箱，我们会在处理完成后回复您。
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setSubmitted(false)}
              className="mt-2"
            >
              再提交一条
            </Button>
          </div>
        ) : (
          <Form methods={methods} onSubmit={onSubmit}>
            <Field.Select
              name="subject"
              label="反馈类型"
              options={[...FEEDBACK_TYPES]}
              placeholder="请选择"
            />
            <Field.Text
              name="email"
              label="邮箱（选填）"
              type="email"
              placeholder="留下邮箱以便我们回复您"
            />
            <Field.Textarea
              name="content"
              label="详细描述"
              placeholder="请详细描述遇到的问题或建议（5-2000 字）"
              rows={8}
              allowRichText={false}
            />
            <Button
              type="submit"
              disabled={methods.formState.isSubmitting}
              className="w-full md:w-auto"
            >
              {methods.formState.isSubmitting ? "提交中..." : "提交反馈"}
            </Button>
          </Form>
        )}
      </section>
    </main>
  )
}
