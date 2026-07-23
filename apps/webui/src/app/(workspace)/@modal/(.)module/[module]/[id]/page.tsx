import { EntityRecordInterceptModal } from "@/sections/entity/view/EntityRecordInterceptModal"

interface PageProps {
  params: Promise<{ module: string; id: string }>
  searchParams: Promise<{ qw?: string }>
}

/** 从工作区内导航到记录详情时，在 @modal 插槽中拦截并显示详情弹窗。 */
export default async function EntityRecordInterceptRoute({ params, searchParams }: PageProps) {
  const { module, id } = await params
  const { qw } = await searchParams

  return <EntityRecordInterceptModal module={module} recordId={id} queryToken={qw} />
}
