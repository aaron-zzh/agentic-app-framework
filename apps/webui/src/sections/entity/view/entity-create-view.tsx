/**
 * EntityCreateView——实体新建视图
 * @author AaronZZH & Kiro
 *
 * 与 EntityRecordView（编辑/详情）独立：
 * - 不发 GET 请求拉取数据（避免把 "new" 当作 ID 误传到后端）
 * - 渲染空表单（FormView 不传 data）
 * - 提交时调 useCrudCreate；成功后返回实体列表
 */

"use client"

import { useRouter } from "next/navigation"
import { Suspense, useCallback } from "react"
import { toast } from "sonner"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { PageContainer } from "@/components/common/PageContainer"
import { Card } from "@/components/ui/card"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { FormView } from "@/features/entity-engine/components/form/FormView"
import { useResolvedEntity } from "@/features/entity-engine/hooks/use-resolved-entity"
import type { EntityDef } from "@/features/entity-engine/types"
import { fromEntityDef, useCrudCreate } from "@/lib/api/rest/crud"
import { paths } from "@/lib/constants/paths"
import { selectScopeReadOnly, useOrgStore } from "@/lib/store/org-store"

interface Props {
  entity: EntityDef
  presentation?: "page" | "dialog"
}

export function EntityCreateView({ entity, presentation = "page" }: Props) {
  const scopeReadOnly = useOrgStore(selectScopeReadOnly)
  const router = useRouter()
  const resolvedEntity = useResolvedEntity(entity)
  const resource = fromEntityDef(entity)
  const { mutate: create, isPending } = useCrudCreate(resource)
  const isDialog = presentation === "dialog"

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (!open) router.back()
    },
    [router]
  )

  const handleSubmit = (values: Record<string, unknown>) => {
    create(values, {
      onSuccess: () => {
        toast.success(`${entity.label}已创建`)
        if (isDialog) {
          router.back()
          return
        }
        router.replace(paths.workspace.module(entity.slug))
      },
      onError: () => {}
    })
  }

  if (scopeReadOnly) {
    return (
      <PageContainer maxWidth="lg" className="flex flex-1 flex-col">
        <CustomBreadcrumbs
          links={[
            { name: "首页", href: paths.workspace.root },
            { name: entity.label, href: paths.workspace.module(entity.slug) },
            { name: "新建" }
          ]}
          className="mb-4"
        />
        <Card className="p-6">
          <h1 className="font-medium">当前范围为只读</h1>
          <p className="mt-2 text-muted-foreground text-sm">
            聚合范围不能创建数据，请先选择一个具体工作区。
          </p>
        </Card>
      </PageContainer>
    )
  }

  const form = (
    <Suspense>
      <FormView entity={resolvedEntity} loading={isPending} onSubmit={handleSubmit} mode="create" />
    </Suspense>
  )

  if (isDialog) {
    return (
      <Dialog open onOpenChange={handleOpenChange}>
        <DialogContent className="flex max-h-[calc(100vh-2rem)] w-full max-w-[calc(100%-2rem)] flex-col gap-0 p-0 sm:max-w-3xl">
          <DialogHeader className="shrink-0 border-b px-5 py-4 pr-12">
            <DialogTitle>新建{entity.label}</DialogTitle>
          </DialogHeader>
          <div className="min-h-0 flex-1 overflow-auto">{form}</div>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <PageContainer maxWidth="lg" className="flex flex-1 flex-col">
      <CustomBreadcrumbs
        links={[
          { name: "首页", href: paths.workspace.root },
          { name: entity.label, href: paths.workspace.module(entity.slug) },
          { name: "新建" }
        ]}
        className="mb-4"
      />

      <Card className="flex flex-1 flex-col overflow-hidden py-0">{form}</Card>
    </PageContainer>
  )
}
