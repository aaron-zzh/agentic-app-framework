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
import { Field } from "@/components/form/fields"
import { Form } from "@/components/form/form"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { RichTextEditor } from "@/features/rich-text-editor"
import {
  useCreateDocument,
  usePublishDocument,
  useUnpublishDocument,
  useUpdateDocument
} from "@/lib/api/rest/system"
import { paths } from "@/lib/constants/paths"
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
  content: z.string(),
  changeSummary: z.string().max(500, "修改说明不能超过 500 字").optional(),
  publish: z.enum(["draft", "published"])
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
  const { mutate: publishDocument, isPending: publishing } = usePublishDocument()
  const { mutate: unpublishDocument, isPending: unpublishing } = useUnpublishDocument()
  const isPending = creating || updating || publishing || unpublishing

  const methods = useForm<FormValues, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: doc
      ? {
          title: doc.title,
          filePath: doc.filePath ?? "",
          docType: doc.docType,
          content: doc.content ?? "",
          changeSummary: "",
          publish:
            doc.publishedVersionId != null && doc.publishedVersionId === doc.currentVersionId
              ? "published"
              : "draft"
        }
      : {
          title: "",
          filePath: "",
          docType: "guide",
          content: "",
          changeSummary: "",
          publish: "draft"
        }
  })

  function onSubmit(values: FormValues) {
    if (doc) {
      update(
        {
          id: doc.id,
          title: values.title,
          content: values.content,
          expectedCurrentVersionId: doc.currentVersionId,
          changeSummary: values.changeSummary,
          docType: values.docType
        },
        {
          onSuccess: (saved) => {
            const complete = () => {
              toast.success("文档已保存")
              router.push(paths.docs.root)
            }
            if (!methods.formState.dirtyFields.publish) {
              complete()
              return
            }
            if (values.publish === "published") {
              if (!saved.currentVersionId) {
                toast.error("文档当前版本不存在，请刷新后重试")
                return
              }
              publishDocument(
                { id: saved.id, expectedCurrentVersionId: saved.currentVersionId },
                { onSuccess: complete, onError: () => toast.error("发布失败") }
              )
              return
            }
            unpublishDocument(saved.id, {
              onSuccess: complete,
              onError: () => toast.error("取消发布失败")
            })
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

  const publish = methods.watch("publish")

  return (
    <Form methods={methods} onSubmit={onSubmit} className="mx-auto max-w-4xl space-y-6 p-6">
      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-xl">{doc ? "编辑文档" : "新建文档"}</h1>
        <div className="flex items-center gap-2">
          <Badge variant={publish === "published" ? "default" : "secondary"}>
            {publish === "published" ? "将发布当前版本" : "草稿"}
          </Badge>
          <Button type="button" variant="outline" onClick={() => router.back()}>
            取消
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() =>
              methods.setValue("publish", publish === "published" ? "draft" : "published", {
                shouldDirty: true
              })
            }
          >
            {publish === "published" ? "转为草稿" : "发布当前版本"}
          </Button>
          <Button type="submit" disabled={isPending}>
            {isPending ? "保存中..." : "保存"}
          </Button>
        </div>
      </div>

      {/* 基本信息 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">基本信息</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Field.Text name="title" label="标题" placeholder="文档标题" />
          {doc && (
            <Field.Text name="changeSummary" label="版本说明" placeholder="简要说明本次修改" />
          )}
          <div className="grid grid-cols-2 gap-4">
            <Field.Text name="filePath" label="文件路径" placeholder="docs/design/xxx.md" />
            <Field.Select name="docType" label="文档类型" options={DOC_TYPES} />
          </div>
        </CardContent>
      </Card>

      {/* 内容编辑器（直接用 Controller 绑定富文本） */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">内容</CardTitle>
        </CardHeader>
        <CardContent>
          <RichTextEditor
            value={methods.watch("content")}
            onChange={(v) => methods.setValue("content", v)}
            preset="document"
            mode="markdown"
            minHeight={500}
          />
        </CardContent>
      </Card>
    </Form>
  )
}
