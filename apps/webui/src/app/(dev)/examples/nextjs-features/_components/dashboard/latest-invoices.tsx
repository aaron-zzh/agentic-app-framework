import clsx from "clsx"
import { RefreshCw as ArrowPathIcon } from "lucide-react"

import type { Invoice } from "../../_data/mock"

export default function LatestInvoices({ latestInvoices }: { latestInvoices: Invoice[] }) {
  return (
    <div className="flex w-full flex-col md:col-span-4">
      <h2 className="mb-4 font-bold text-slate-700 text-xl">Latest Invoices</h2>
      <div className="flex grow flex-col justify-between rounded-xl bg-gray-50 p-4">
        <div className="bg-white px-6">
          {latestInvoices.map((invoice, i) => (
            <div
              key={invoice.id}
              className={clsx("flex flex-row items-center justify-between py-4", {
                "border-t": i !== 0
              })}
            >
              <div className="min-w-0">
                <p className="truncate font-semibold text-slate-700 text-sm">{invoice.name}</p>
                <p className="hidden text-gray-500 text-sm sm:block">{invoice.email}</p>
              </div>
              <p className="truncate font-medium text-sm">${(invoice.amount / 100).toFixed(2)}</p>
            </div>
          ))}
        </div>
        <div className="flex items-center pt-3 pb-2">
          <ArrowPathIcon className="h-4 w-4 text-gray-500" />
          <h3 className="ml-2 text-gray-500 text-sm">Updated just now</h3>
        </div>
      </div>
    </div>
  )
}
