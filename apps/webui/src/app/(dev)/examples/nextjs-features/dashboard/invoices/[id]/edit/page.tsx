/**
 * 特性演示：动态路由 [id] + notFound() + 并行数据获取
 *
 * 1. params 是 Promise（Next.js 15+ 异步化）
 * 2. fetchInvoiceById 返回 null 时调用 notFound()，
 *    自动渲染就近的 not-found.tsx
 * 3. Promise.all 并行请求，避免串行瀑布
 */
import { notFound } from "next/navigation"
import Breadcrumbs from "../../../../_components/invoices/breadcrumbs"
import EditInvoiceForm from "../../../../_components/invoices/edit-form"
import { fetchCustomers, fetchInvoiceById } from "../../../../_data/mock"

export default async function EditInvoicePage(props: { params: Promise<{ id: string }> }) {
  const { id } = await props.params
  const [invoice, customers] = await Promise.all([fetchInvoiceById(id), fetchCustomers()])

  if (!invoice) {
    notFound()
  }

  return (
    <main>
      <Breadcrumbs
        breadcrumbs={[
          { label: "Invoices", href: "/examples/nextjs-features/dashboard/invoices" },
          {
            label: "Edit Invoice",
            href: `/examples/nextjs-features/dashboard/invoices/${id}/edit`,
            active: true
          }
        ]}
      />
      <EditInvoiceForm invoice={invoice} customers={customers} />
    </main>
  )
}
