/**
 * 审批表单模板 TanStack Query Hooks
 * @author Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { request } from "@/lib/api/client"
import type { FormTemplate } from "../components/vertical-designer/types"

/** 查询表单模板列表 */
export function useFormTemplates() {
  return useQuery({
    queryKey: ["form-templates"],
    queryFn: () => request<FormTemplate[]>("/system/workflow/form-templates")
  })
}

/** 查询单个表单模板 */
export function useFormTemplate(id?: string) {
  return useQuery({
    queryKey: ["form-templates", id],
    queryFn: () => request<FormTemplate>(`/system/workflow/form-templates/${id}`),
    enabled: !!id
  })
}

/** 创建表单模板 */
export function useCreateFormTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Omit<FormTemplate, "id" | "createdAt" | "updatedAt">) =>
      request<FormTemplate>("/system/workflow/form-templates", {
        method: "POST",
        body: JSON.stringify(body)
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["form-templates"] })
    }
  })
}

/** 更新表单模板 */
export function useUpdateFormTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Pick<FormTemplate, "id" | "name" | "fields">) =>
      request<FormTemplate>(`/system/workflow/form-templates/${body.id}`, {
        method: "PUT",
        body: JSON.stringify(body)
      }),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["form-templates"] })
      qc.invalidateQueries({ queryKey: ["form-templates", vars.id] })
    }
  })
}
