/**
 * 特性演示：Server Components 数据预取 + 面包屑导航
 *
 * 在服务端预取客户列表传给表单，无需客户端请求，
 * 减少客户端 JS 体积，同时保证表单首次渲染即有数据。
 */

import Breadcrumbs from "../../../_components/invoices/breadcrumbs"
import CreateInvoiceForm from "../../../_components/invoices/create-form"
import { fetchCustomers } from "../../../_data/mock"

export default async function CreateInvoicePage() {
  const customers = await fetchCustomers()

  return (
    <main>
      <Breadcrumbs
        breadcrumbs={[
          { label: "Invoices", href: "/examples/nextjs-features/dashboard/invoices" },
          {
            label: "Create Invoice",
            href: "/examples/nextjs-features/dashboard/invoices/create",
            active: true
          }
        ]}
      />
      <CreateInvoiceForm customers={customers} />
    </main>
  )
}
