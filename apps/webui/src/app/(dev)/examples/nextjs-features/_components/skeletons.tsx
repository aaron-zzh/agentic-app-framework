/**
 * 特性演示：骨架屏（Skeleton）组件
 *
 * shimmer 动画：使用 Tailwind 的 before: 伪元素 + 平移动画模拟扫光效果。
 * 与 Suspense fallback 配合，在数据加载时提供视觉占位，避免布局抖动（CLS）。
 */

const shimmer =
  "before:absolute before:inset-0 before:-translate-x-full before:animate-[shimmer_2s_infinite] before:bg-gradient-to-r before:from-transparent before:via-white/60 before:to-transparent"

export function CardSkeleton() {
  return (
    <div className={`${shimmer} relative overflow-hidden rounded-xl bg-gray-100 p-2 shadow-sm`}>
      <div className="flex p-4">
        <div className="h-5 w-5 rounded-md bg-gray-200" />
        <div className="ml-2 h-6 w-16 rounded-md bg-gray-200" />
      </div>
      <div className="flex items-center justify-center truncate rounded-xl bg-white px-4 py-8">
        <div className="h-7 w-20 rounded-md bg-gray-200" />
      </div>
    </div>
  )
}

export function CardsSkeleton() {
  return (
    <>
      {Array.from({ length: 4 }).map((_, i) => (
        <CardSkeleton key={i} />
      ))}
    </>
  )
}

export function RevenueChartSkeleton() {
  return (
    <div className={`${shimmer} relative w-full overflow-hidden md:col-span-4`}>
      <div className="mb-4 h-8 w-36 rounded-md bg-gray-100" />
      <div className="rounded-xl bg-gray-100 p-4">
        <div className="mt-0 grid h-52 grid-cols-12 items-end gap-2 rounded-md bg-white p-4 md:gap-4" />
        <div className="flex items-center pt-3 pb-2">
          <div className="h-4 w-4 rounded-full bg-gray-200" />
          <div className="ml-2 h-4 w-20 rounded-md bg-gray-200" />
        </div>
      </div>
    </div>
  )
}

export function InvoiceSkeleton() {
  return (
    <div className="flex flex-row items-center justify-between border-gray-100 border-b py-4">
      <div className="flex items-center">
        <div className="mr-4 h-8 w-8 rounded-full bg-gray-200" />
        <div>
          <div className="h-5 w-40 rounded-md bg-gray-200" />
          <div className="mt-1 h-4 w-24 rounded-md bg-gray-200" />
        </div>
      </div>
      <div className="h-4 w-12 rounded-md bg-gray-200" />
    </div>
  )
}

export function LatestInvoicesSkeleton() {
  return (
    <div className={`${shimmer} relative flex w-full flex-col overflow-hidden md:col-span-4`}>
      <div className="mb-4 h-8 w-36 rounded-md bg-gray-100" />
      <div className="flex grow flex-col justify-between rounded-xl bg-gray-100 p-4">
        <div className="bg-white px-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <InvoiceSkeleton key={i} />
          ))}
        </div>
      </div>
    </div>
  )
}

export default function DashboardSkeleton() {
  return (
    <>
      <div className={`${shimmer} relative mb-4 h-8 w-36 overflow-hidden rounded-md bg-gray-100`} />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <CardsSkeleton />
      </div>
      <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-4 lg:grid-cols-8">
        <RevenueChartSkeleton />
        <LatestInvoicesSkeleton />
      </div>
    </>
  )
}

export function TableRowSkeleton() {
  return (
    <tr className="w-full border-gray-100 border-b">
      <td className="whitespace-nowrap py-3 pr-3 pl-4">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-full bg-gray-100" />
          <div className="h-6 w-24 rounded bg-gray-100" />
        </div>
      </td>
      <td className="px-3 py-3">
        <div className="h-6 w-32 rounded bg-gray-100" />
      </td>
      <td className="px-3 py-3">
        <div className="h-6 w-16 rounded bg-gray-100" />
      </td>
      <td className="px-3 py-3">
        <div className="h-6 w-16 rounded bg-gray-100" />
      </td>
      <td className="px-3 py-3">
        <div className="h-6 w-16 rounded bg-gray-100" />
      </td>
      <td className="py-3 pr-3 pl-6">
        <div className="flex justify-end gap-2">
          <div className="h-8 w-8 rounded bg-gray-100" />
          <div className="h-8 w-8 rounded bg-gray-100" />
        </div>
      </td>
    </tr>
  )
}

export function InvoicesTableSkeleton() {
  return (
    <div className="mt-6 flow-root">
      <div className="inline-block min-w-full align-middle">
        <div className="rounded-lg bg-gray-50 p-2 md:pt-0">
          <table className="min-w-full text-gray-900">
            <thead className="rounded-lg text-left font-normal text-sm">
              <tr>
                {["Customer", "Email", "Amount", "Date", "Status", ""].map((h) => (
                  <th key={h} className="px-3 py-5 font-medium">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white">
              {Array.from({ length: 6 }).map((_, i) => (
                <TableRowSkeleton key={i} />
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
