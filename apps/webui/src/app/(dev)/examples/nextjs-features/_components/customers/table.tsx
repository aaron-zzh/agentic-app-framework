import type { Customer } from "../../_data/mock"
import Search from "../search"

export default function CustomersTable({ customers }: { customers: Customer[] }) {
  return (
    <div className="w-full">
      <h1 className="mb-8 font-bold text-slate-800 text-xl md:text-2xl">Customers</h1>
      <Search placeholder="Search customers..." />
      <div className="mt-6 overflow-x-auto">
        <table className="min-w-full text-gray-900">
          <thead className="rounded-lg text-left font-normal text-sm">
            <tr>
              {["Name", "Email", "Total Invoices", "Total Pending", "Total Paid"].map((h) => (
                <th key={h} scope="col" className="px-4 py-5 font-medium">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 bg-white">
            {customers.map((customer) => (
              <tr key={customer.id}>
                <td className="whitespace-nowrap py-5 pr-3 pl-4 font-medium text-sm">
                  {customer.name}
                </td>
                <td className="whitespace-nowrap px-4 py-5 text-gray-500 text-sm">
                  {customer.email}
                </td>
                <td className="whitespace-nowrap px-4 py-5 text-sm">{customer.total_invoices}</td>
                <td className="whitespace-nowrap px-4 py-5 text-sm">{customer.total_pending}</td>
                <td className="whitespace-nowrap px-4 py-5 text-sm">{customer.total_paid}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
