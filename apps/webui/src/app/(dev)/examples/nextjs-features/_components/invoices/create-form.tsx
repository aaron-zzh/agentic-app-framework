/**
 * 特性演示：useActionState（React 19）
 *
 * useActionState(action, initialState) 返回 [state, formAction]：
 * - formAction：传给 <form action={}>，提交时调用服务端 action
 * - state：服务端返回的错误状态，用于显示验证错误
 *
 * 渐进增强：即使 JS 禁用，表单仍可正常提交（原生 HTML form）
 */
"use client"

import {
  Check as CheckIcon,
  Clock as ClockIcon,
  DollarSign as CurrencyDollarIcon,
  UserCircle as UserCircleIcon
} from "lucide-react"
import Link from "next/link"
import { useActionState } from "react"
import { Button } from "@/components/ui/button"
import { createInvoice, type State } from "../../_actions/invoice"
import type { Customer } from "../../_data/mock"

export default function CreateInvoiceForm({ customers }: { customers: Customer[] }) {
  const initialState: State = { message: null, errors: {} }
  const [state, formAction] = useActionState(createInvoice, initialState)

  return (
    <form action={formAction}>
      <div className="rounded-md bg-gray-50 p-4 md:p-6">
        {/* 客户选择 */}
        <div className="mb-4">
          <label htmlFor="customer" className="mb-2 block font-medium text-sm">
            选择客户
          </label>
          <div className="relative">
            <select
              id="customer"
              name="customerId"
              className="peer block w-full cursor-pointer rounded-md border border-gray-200 py-2 pl-10 text-sm"
              defaultValue=""
              aria-describedby="customer-error"
            >
              <option value="" disabled>
                Select a customer
              </option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <UserCircleIcon className="pointer-events-none absolute top-1/2 left-3 h-5 w-5 -translate-y-1/2 text-gray-500" />
          </div>
          {/* 服务端验证错误：aria-live 无障碍公告 */}
          <div id="customer-error" aria-live="polite" aria-atomic="true">
            {state.errors?.customerId?.map((e) => (
              <p key={e} className="mt-2 text-red-500 text-sm">
                {e}
              </p>
            ))}
          </div>
        </div>

        {/* 金额 */}
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
              placeholder="输入金额"
              className="peer block w-full rounded-md border border-gray-200 py-2 pl-10 text-sm"
            />
            <CurrencyDollarIcon className="pointer-events-none absolute top-1/2 left-3 h-5 w-5 -translate-y-1/2 text-gray-500" />
          </div>
          {state.errors?.amount?.map((e) => (
            <p key={e} className="mt-2 text-red-500 text-sm">
              {e}
            </p>
          ))}
        </div>

        {/* 状态 */}
        <fieldset>
          <legend className="mb-2 block font-medium text-sm">发票状态</legend>
          <div className="flex gap-4 rounded-md border border-gray-200 bg-white p-3">
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="radio"
                name="status"
                value="pending"
                className="h-4 w-4 cursor-pointer"
              />
              <span className="flex items-center gap-1 rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-xs">
                Pending <ClockIcon className="h-3 w-3" />
              </span>
            </label>
            <label className="flex cursor-pointer items-center gap-2">
              <input type="radio" name="status" value="paid" className="h-4 w-4 cursor-pointer" />
              <span className="flex items-center gap-1 rounded-full bg-green-500 px-3 py-1 text-white text-xs">
                Paid <CheckIcon className="h-3 w-3" />
              </span>
            </label>
          </div>
          {state.errors?.status?.map((e) => (
            <p key={e} className="mt-2 text-red-500 text-sm">
              {e}
            </p>
          ))}
        </fieldset>

        {state.message && <p className="mt-2 text-red-500 text-sm">{state.message}</p>}
      </div>
      <div className="mt-4 flex justify-end gap-4">
        <Link
          href="/examples/nextjs-features/dashboard/invoices"
          className="flex h-10 items-center rounded-lg bg-gray-100 px-4 text-gray-600 text-sm hover:bg-gray-200"
        >
          取消
        </Link>
        <Button type="submit">创建发票</Button>
      </div>
    </form>
  )
}
