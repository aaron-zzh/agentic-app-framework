/**
 * 文档新建/编辑表单（新建和编辑路由共用）
 * @author AaronZZH & Kiro
 */
"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { RichTextEditor } from "@/features/rich-text-editor"
import { paths } from "@/lib/constants/paths"
import { useCreateDocument, useUpdateDocument } from "@/lib/queries/use-documents"
import type { Document } from "@/lib/types/document"

const DOC_TYPES = [
  { value: "spec", label: "规格" },
  { value: "design", label: "设计" },
  { value: "task", label: "任务" },
  { value: "guide", label: "指南" },
  { value: "reference", label: "参考" },
  { value: "explanation", label: "说明" }
]

const schema = z.object({
  title: z.string().min(1, "请填写标题"),
  filePath: z.string().min(1, "请填写文件路径"),
  docType: z.string().min(1, "请选择类型"),
  content: z.string().default(""),
  publish: z.enum(["draft", "published"]).default("draft")
})

type FormValues = z.infer<typeof schema>

interface DocEditFormProps {
  /** 编辑模式传入现有文档，新建模式不传 */
  doc?: Document
}

export function DocEditForm({ doc }: DocEditFormProps) {
  const router = useRouter()
  const { mutate: create, isPending: creating } = useCreateDocument()
  const { mutate: update, isPending: updating } = useUpdateDocument()
  const isPending = creating || updating

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: doc
      ? {
          title: doc.title,
          filePath: doc.filePath,
          docType: doc.docType,
          content: doc.content ?? "",
          publish: (doc.publish as "draft" | "published") ?? "draft"
        }
      : { title: "", filePath: "", docType: "guide", content: "", publish: "draft" }
  })

  function onSubmit(values: FormValues) {
    if (doc) {
      update(
        { id: doc.id, ...values },
        {
          onSuccess: () => {
            toast.success("文档已保存")
            router.push(paths.docs.root)
          },
          onError: () => toast.error("保存失败")
        }
      )
    } else {
      create(values, {
        onSuccess: () => {
          toast.success("文档已创建")
          router.push(paths.docs.root)
        },
        onError: () => toast.error("创建失败")
      })
    }
  }

  const publish = form.watch("publish")

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="mx-auto max-w-4xl space-y-6 p-6">
        {/* 标题栏 */}
        <div className="flex items-center justify-between">
          <h1 className="font-semibold text-xl">{doc ? "编辑文档" : "新建文档"}</h1>
          <div className="flex items-center gap-2">
            <Badge variant={publish === "published" ? "default" : "secondary"}>
              {publish === "published" ? "已发布" : "草稿"}
            </Badge>
            <Button type="button" variant="outline" onClick={() => router.back()}>
              取消
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                form.setValue("publish", publish === "published" ? "draft" : "published")
              }}
            >
              {publish === "published" ? "转为草稿" : "发布"}
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? "保存中..." : "保存"}
            </Button>
          </div>
        </div>

        {/* 详情卡片 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">基本信息</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <FormField
              control={form.control}
              name="title"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>标题</FormLabel>
                  <FormControl>
                    <Input placeholder="文档标题" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="filePath"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>文件路径</FormLabel>
                    <FormControl>
                      <Input placeholder="docs/design/xxx.md" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="docType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>文档类型</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="选择类型" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {DOC_TYPES.map((t) => (
                          <SelectItem key={t.value} value={t.value}>
                            {t.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* 内容编辑器 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">内容</CardTitle>
          </CardHeader>
          <CardContent>
            <FormField
              control={form.control}
              name="content"
              render={({ field }) => (
                <FormItem>
                  <FormControl>
                    <RichTextEditor
                      value={field.value}
                      onChange={field.onChange}
                      preset="document"
                      mode="markdown"
                      minHeight={500}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
        </Card>
      </form>
    </Form>
  )
}
