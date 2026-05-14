/**
 * Form 包装器——封装 react-hook-form FormProvider + form 标签
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * const methods = useForm({ resolver: zodResolver(schema) })
 * <Form methods={methods} onSubmit={handleSubmit}>
 *   <Field.Text name="email" label="邮箱" />
 *   <Field.Select name="role" label="角色" options={options} />
 * </Form>
 * ```
 */

"use client"

import type { FieldValues, UseFormReturn } from "react-hook-form"
import { FormProvider } from "react-hook-form"

import { cn } from "@/lib/utils/cn"

export interface FormProps<T extends FieldValues = FieldValues> {
  methods: UseFormReturn<T>
  onSubmit?: (data: T) => void
  children: React.ReactNode
  className?: string
}

/** Form 包装器 */
export function Form<T extends FieldValues = FieldValues>({
  methods,
  onSubmit,
  children,
  className
}: FormProps<T>) {
  return (
    <FormProvider {...methods}>
      <form
        onSubmit={onSubmit ? methods.handleSubmit(onSubmit) : undefined}
        noValidate
        autoComplete="off"
        className={cn("space-y-4", className)}
      >
        {children}
      </form>
    </FormProvider>
  )
}
