/**
 * 新建待办表单——单字段（标题），Enter 提交，提交后自动清空
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2, Plus } from "lucide-react"
import { Controller, useForm } from "react-hook-form"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useStudioTodoCreate } from "@/lib/api/rest/system/todo"
import { notify } from "@/lib/notification"

const newTodoSchema = z.object({
  title: z.string().trim().min(1, "请输入待办内容")
})
type NewTodoFormValues = z.infer<typeof newTodoSchema>

export function TodoNewForm() {
  const { mutate: createTodo, isPending } = useStudioTodoCreate()
  const { control, handleSubmit, reset } = useForm<NewTodoFormValues>({
    resolver: zodResolver(newTodoSchema),
    defaultValues: { title: "" }
  })

  const onSubmit = (values: NewTodoFormValues) => {
    createTodo(
      { title: values.title.trim() },
      {
        onSuccess: () => reset({ title: "" }),
        onError: () => notify.error("新建待办失败，请重试")
      }
    )
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate autoComplete="off" className="flex gap-2">
      <Controller
        name="title"
        control={control}
        render={({ field }) => (
          <Input
            {...field}
            placeholder="有什么需要完成的？按 Enter 添加"
            disabled={isPending}
            autoFocus
            className="h-12 flex-1 text-base"
          />
        )}
      />
      <Button type="submit" size="lg" disabled={isPending} className="h-12 px-4">
        {isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
        添加
      </Button>
    </form>
  )
}
