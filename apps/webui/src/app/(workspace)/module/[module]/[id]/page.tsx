/**
 * 实体记录详情页——表单视图 + RecordPanel 布局
 * @author AaronZZH & Kiro
 */

import { EntityModuleRoute } from "@/sections/entity/view"

interface PageProps {
  params: Promise<{ module: string; id: string }>
  searchParams: Promise<{ qw?: string }>
}

export default async function RecordPage({ params, searchParams }: PageProps) {
  const { module, id } = await params
  const { qw } = await searchParams

  return <EntityModuleRoute kind="record" module={module} recordId={id} queryToken={qw} />
}
