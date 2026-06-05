/**
 * 特性演示：Server Actions（服务端操作）
 *
 * DeleteInvoice 使用原生 <form action={serverAction}> 触发服务端函数，
 * 无需 API 路由，Next.js 自动生成安全的 RPC 调用。
 * CreateInvoice / UpdateInvoice 使用 Link 导航到专用页面处理。
 */

import { Pencil as PencilIcon, Plus as PlusIcon, Trash2 as TrashIcon } from "lucide-react"
import Link from "next/link"

import { deleteInvoice } from "../../_actions/invoice"

export function CreateInvoice() {
  return (
    <Link
      href="/examples/nextjs-features/dashboard/invoices/create"
      className="flex h-10 items-center rounded-lg bg-blue-600 px-4 font-medium text-sm text-white transition-colors hover:bg-blue-500"
    >
      <span className="hidden md:block">Create Invoice</span>
      <PlusIcon className="h-5 md:ml-4" />
    </Link>
  )
}

export function UpdateInvoice({ id }: { id: string }) {
  return (
    <Link
      href={`/examples/nextjs-features/dashboard/invoices/${id}/edit`}
      className="rounded-md border p-2 hover:bg-gray-100"
    >
      <PencilIcon className="w-4" />
    </Link>
  )
}

export function DeleteInvoice({ id }: { id: string }) {
  const deleteInvoiceWithId = deleteInvoice.bind(null, id)
  return (
    <form action={deleteInvoiceWithId}>
      <button type="submit" className="rounded-md border p-2 hover:bg-gray-100">
        <span className="sr-only">Delete</span>
        <TrashIcon className="w-4" />
      </button>
    </form>
  )
}
