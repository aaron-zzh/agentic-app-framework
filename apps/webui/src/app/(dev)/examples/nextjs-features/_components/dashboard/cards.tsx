/**
 * 特性演示：async Server Component
 *
 * CardWrapper 自身是 async 服务端组件，内部直接 await 数据。
 * 被 <Suspense> 包裹后，数据加载期间显示骨架屏，完成后流式替换。
 */
import {
  Banknote as BanknotesIcon,
  Clock as ClockIcon,
  Inbox as InboxIcon,
  Users as UserGroupIcon
} from "lucide-react"

import { fetchCardData } from "../../_data/mock"

const iconMap = {
  collected: BanknotesIcon,
  customers: UserGroupIcon,
  pending: ClockIcon,
  invoices: InboxIcon
}

export default async function CardWrapper() {
  const { numberOfInvoices, numberOfCustomers, totalPaidInvoices, totalPendingInvoices } =
    await fetchCardData()

  return (
    <>
      <Card title="Collected" value={totalPaidInvoices} type="collected" />
      <Card title="Pending" value={totalPendingInvoices} type="pending" />
      <Card title="Total Invoices" value={numberOfInvoices} type="invoices" />
      <Card title="Total Customers" value={numberOfCustomers} type="customers" />
    </>
  )
}

export function Card({
  title,
  value,
  type
}: {
  title: string
  value: number | string
  type: keyof typeof iconMap
}) {
  const Icon = iconMap[type]
  return (
    <div className="rounded-xl bg-gray-50 p-2 shadow-sm">
      <div className="flex p-4">
        <Icon className="h-5 w-5 text-gray-700" />
        <h3 className="ml-2 font-medium text-sm">{title}</h3>
      </div>
      <p className="truncate rounded-xl bg-white px-4 py-8 text-center font-bold text-2xl">
        {value}
      </p>
    </div>
  )
}
