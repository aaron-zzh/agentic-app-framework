import { fetchFilteredInvoices, formatCurrency, formatDateToLocal } from "../../_data/mock"
import { DeleteInvoice, UpdateInvoice } from "./buttons"
import InvoiceStatus from "./status"

export default async function InvoicesTable({
  query,
  currentPage
}: {
  query: string
  currentPage: number
}) {
  const invoices = await fetchFilteredInvoices(query, currentPage)

  return (
    <div className="mt-6 flow-root">
      <div className="inline-block min-w-full align-middle">
        <div className="rounded-lg bg-gray-50 p-2 md:pt-0">
          <table className="hidden min-w-full text-gray-900 md:table">
            <thead className="rounded-lg text-left font-normal text-sm">
              <tr>
                {["Customer", "Email", "Amount", "Date", "Status", ""].map((h) => (
                  <th key={h} scope="col" className="px-4 py-5 font-medium">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white">
              {invoices.map((invoice) => (
                <tr
                  key={invoice.id}
                  className="w-full border-b py-3 text-sm last-of-type:border-none"
                >
                  <td className="whitespace-nowrap py-3 pr-3 pl-6 font-medium">{invoice.name}</td>
                  <td className="whitespace-nowrap px-3 py-3 text-gray-500">{invoice.email}</td>
                  <td className="whitespace-nowrap px-3 py-3">
                    {formatCurrency(invoice.amount * 100)}
                  </td>
                  <td className="whitespace-nowrap px-3 py-3">{formatDateToLocal(invoice.date)}</td>
                  <td className="whitespace-nowrap px-3 py-3">
                    <InvoiceStatus status={invoice.status} />
                  </td>
                  <td className="whitespace-nowrap py-3 pr-3 pl-6">
                    <div className="flex justify-end gap-3">
                      <UpdateInvoice id={invoice.id} />
                      <DeleteInvoice id={invoice.id} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {/* 移动端卡片视图 */}
          <div className="space-y-2 md:hidden">
            {invoices.map((invoice) => (
              <div key={invoice.id} className="rounded-md bg-white p-4">
                <div className="flex items-center justify-between border-b pb-4">
                  <div>
                    <p className="font-medium">{invoice.name}</p>
                    <p className="text-gray-500 text-sm">{invoice.email}</p>
                  </div>
                  <InvoiceStatus status={invoice.status} />
                </div>
                <div className="flex w-full items-center justify-between pt-4">
                  <div>
                    <p className="font-medium text-xl">{formatCurrency(invoice.amount * 100)}</p>
                    <p className="text-gray-500 text-sm">{formatDateToLocal(invoice.date)}</p>
                  </div>
                  <div className="flex gap-2">
                    <UpdateInvoice id={invoice.id} />
                    <DeleteInvoice id={invoice.id} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
