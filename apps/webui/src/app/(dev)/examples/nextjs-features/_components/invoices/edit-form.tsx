"use client"

import {
  Check as CheckIcon,
  Clock as ClockIcon,
  DollarSign as CurrencyDollarIcon,
  UserCircle as UserCircleIcon
} from "lucide-react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { updateInvoice } from "../../_actions/invoice"
import type { Customer, Invoice } from "../../_data/mock"

export default function EditInvoiceForm({
  invoice,
  customers
}: {
  invoice: Invoice
  customers: Customer[]
}) {
  // .bind() 预填充 id，形成新函数传给 form action
  const updateInvoiceWithId = updateInvoice.bind(null, invoice.id)

  return (
    <form action={updateInvoiceWithId}>
      <div className="rounded-md bg-gray-50 p-4 md:p-6">
        <div className="mb-4">
          <label htmlFor="customer" className="mb-2 block font-medium text-sm">
            选择客户
          </label>
          <div className="relative">
            <select
              id="customer"
              name="customerId"
              defaultValue={invoice.customer_id}
              className="peer block w-full cursor-pointer rounded-md border border-gray-200 py-2 pl-10 text-sm"
            >
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <UserCircleIcon className="pointer-events-none absolute top-1/2 left-3 h-5 w-5 -translate-y-1/2 text-gray-500" />
          </div>
        </div>
        <div className="mb-4">
          <label htmlFor="amount" className="mb-2 block font-medium text-sm">
            金额（USD）
          </label>
          <div className="relative">
            <input
              id="amount"
              name="amount"
              type="number"
              step="0.01"
              defaultValue={invoice.amount}
              className="peer block w-full rounded-md border border-gray-200 py-2 pl-10 text-sm"
            />
            <CurrencyDollarIcon className="pointer-events-none absolute top-1/2 left-3 h-5 w-5 -translate-y-1/2 text-gray-500" />
          </div>
        </div>
        <fieldset>
          <legend className="mb-2 block font-medium text-sm">发票状态</legend>
          <div className="flex gap-4 rounded-md border border-gray-200 bg-white p-3">
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="radio"
                name="status"
                value="pending"
                defaultChecked={invoice.status === "pending"}
                className="h-4 w-4"
              />
              <span className="flex items-center gap-1 rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-xs">
                Pending <ClockIcon className="h-3 w-3" />
              </span>
            </label>
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="radio"
                name="status"
                value="paid"
                defaultChecked={invoice.status === "paid"}
                className="h-4 w-4"
              />
              <span className="flex items-center gap-1 rounded-full bg-green-500 px-3 py-1 text-white text-xs">
                Paid <CheckIcon className="h-3 w-3" />
              </span>
            </label>
          </div>
        </fieldset>
      </div>
      <div className="mt-4 flex justify-end gap-4">
        <Link
          href="/examples/nextjs-features/dashboard/invoices"
          className="flex h-10 items-center rounded-lg bg-gray-100 px-4 text-gray-600 text-sm hover:bg-gray-200"
        >
          取消
        </Link>
        <Button type="submit">保存修改</Button>
      </div>
    </form>
  )
}
