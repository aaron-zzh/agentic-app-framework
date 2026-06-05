/**
 * 社交链接设置——对标 minimal-ui AccountSocials
 * 每个链接前缀带品牌图标
 * @author Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Globe } from "lucide-react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Form } from "@/components/form/form"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { notify } from "@/lib/notification"

const socialsSchema = z.object({
  facebook: z.string().url("请输入有效 URL").or(z.literal("")).optional(),
  instagram: z.string().url("请输入有效 URL").or(z.literal("")).optional(),
  linkedin: z.string().url("请输入有效 URL").or(z.literal("")).optional(),
  twitter: z.string().url("请输入有效 URL").or(z.literal("")).optional(),
  github: z.string().url("请输入有效 URL").or(z.literal("")).optional(),
  website: z.string().url("请输入有效 URL").or(z.literal("")).optional()
})

type SocialsFormValues = z.infer<typeof socialsSchema>

const SOCIAL_FIELDS: {
  name: keyof SocialsFormValues
  label: string
  placeholder: string
  icon: string
}[] = [
  { name: "facebook", label: "Facebook", placeholder: "https://facebook.com/username", icon: "𝐟" },
  {
    name: "instagram",
    label: "Instagram",
    placeholder: "https://instagram.com/username",
    icon: "📸"
  },
  {
    name: "linkedin",
    label: "LinkedIn",
    placeholder: "https://linkedin.com/in/username",
    icon: "in"
  },
  { name: "twitter", label: "Twitter / X", placeholder: "https://x.com/username", icon: "𝕏" },
  { name: "github", label: "GitHub", placeholder: "https://github.com/username", icon: "⌥" },
  { name: "website", label: "个人网站", placeholder: "https://yoursite.com", icon: "" }
]

export default function SocialsPage() {
  const methods = useForm<SocialsFormValues>({
    resolver: zodResolver(socialsSchema),
    defaultValues: {
      facebook: "",
      instagram: "",
      linkedin: "",
      twitter: "",
      github: "",
      website: ""
    }
  })

  const onSubmit = async (_data: SocialsFormValues) => {
    try {
      await new Promise((r) => setTimeout(r, 400))

      notify.success("社交链接已保存")
    } catch {
      notify.error("保存失败，请重试")
    }
  }

  return (
    <div className="p-6">
      <Form methods={methods} onSubmit={onSubmit}>
        <Card>
          <CardHeader>
            <CardTitle>社交链接</CardTitle>
            <CardDescription>展示在你的公开主页上</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {SOCIAL_FIELDS.map(({ name, label, placeholder, icon }) => {
              const err = methods.formState.errors[name]
              return (
                <div key={name} className="space-y-1.5">
                  <Label htmlFor={name}>{label}</Label>
                  <div className="flex items-center gap-2">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-md border bg-muted font-bold text-sm">
                      {icon || <Globe className="size-4" />}
                    </span>
                    <Input id={name} placeholder={placeholder} {...methods.register(name)} />
                  </div>
                  {err && <p className="text-destructive text-xs">{err.message}</p>}
                </div>
              )
            })}

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={methods.formState.isSubmitting}>
                {methods.formState.isSubmitting ? "保存中..." : "保存修改"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </Form>
    </div>
  )
}
