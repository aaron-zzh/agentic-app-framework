/**
 * EntityCreateView——实体新建视图
 * @author AaronZZH & Kiro
 *
 * 与 EntityRecordView（编辑/详情）独立：
 * - 不发 GET 请求拉取数据（避免把 "new" 当作 ID 误传到后端）
 * - 渲染空表单（FormView 不传 data）
 * - 提交时调 useCrudCreate；成功后跳转到新记录的详情页
 */

"use client"

import { useRouter } from "next/navigation"
import { Suspense } from "react"
import { toast } from "sonner"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { Card } from "@/components/ui/card"
import { FormView } from "@/features/entity-engine/components/form/FormView"
import type { EntityDef } from "@/features/entity-engine/types"
import { fromEntityDef, useCrudCreate } from "@/lib/api/rest/crud"
import { paths } from "@/lib/constants/paths"

interface Props {
  entity: EntityDef
}

export function EntityCreateView({ entity }: Props) {
  const router = useRouter()
  const resource = fromEntityDef(entity)
  const { mutate: create, isPending } = useCrudCreate(resource)

  const handleSubmit = (values: Record<string, unknown>) => {
    create(values, {
      onSuccess: (record) => {
        toast.success(`${entity.label}已创建`)
        const id = (record as { id?: string | number }).id
        if (id != null) {
          router.replace(paths.workspace.record(entity.slug, String(id)))
        } else {
          router.replace(paths.workspace.module(entity.slug))
        }
      },
      onError: () => {}
    })
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden p-3">
      <CustomBreadcrumbs
        links={[
          { name: "首页", href: paths.workspace.root },
          { name: entity.label, href: paths.workspace.module(entity.slug) },
          { name: "新建" }
        ]}
        className="mb-4"
      />

      <Card className="flex flex-1 flex-col overflow-hidden py-0">
        <Suspense>
          <FormView entity={entity} loading={isPending} onSubmit={handleSubmit} />
        </Suspense>
      </Card>
    </div>
  )
}
