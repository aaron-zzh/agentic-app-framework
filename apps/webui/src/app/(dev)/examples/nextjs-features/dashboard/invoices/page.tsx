/**
 * 特性演示：URL 搜索参数驱动 + Suspense key 重置
 *
 * 核心模式：
 * 1. searchParams 来自服务端组件 props，URL 即状态（单一数据源）
 * 2. Search 组件（客户端）通过 router.replace 修改 URL → 触发服务端重新渲染
 * 3. <Suspense key={query + page}> 当 key 变化时重置 Suspense，
 *    实现"搜索时重新显示骨架屏"的效果
 */

import type { Metadata } from "next"
import { Suspense } from "react"
import { CreateInvoice } from "../../_components/invoices/buttons"
import Pagination from "../../_components/invoices/pagination"
import InvoicesTable from "../../_components/invoices/table"
import Search from "../../_components/search"
import { InvoicesTableSkeleton } from "../../_components/skeletons"
import { fetchInvoicesPages } from "../../_data/mock"

export const metadata: Metadata = { title: "Invoices" }

export default async function InvoicesPage(props: {
  searchParams?: Promise<{ query?: string; page?: string }>
}) {
  const searchParams = await props.searchParams
  const query = searchParams?.query ?? ""
  const currentPage = Number(searchParams?.page) || 1
  const totalPages = await fetchInvoicesPages(query)

  return (
    <div className="w-full">
      <div className="flex w-full items-center justify-between">
        <h1 className="font-bold text-2xl text-slate-800">Invoices</h1>
      </div>
      <div className="mt-4 flex items-center justify-between gap-2 md:mt-8">
        {/* Search：客户端组件，debounce 后 replace URL */}
        <Search placeholder="Search invoices..." />
        <CreateInvoice />
      </div>
      {/* key 变化时 Suspense 重置，重新显示骨架屏 */}
      <Suspense key={query + currentPage} fallback={<InvoicesTableSkeleton />}>
        <InvoicesTable query={query} currentPage={currentPage} />
      </Suspense>
      <div className="mt-5 flex w-full justify-center">
        <Pagination totalPages={totalPages} />
      </div>
    </div>
  )
}
