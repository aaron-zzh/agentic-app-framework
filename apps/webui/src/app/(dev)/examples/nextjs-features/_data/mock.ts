/** Mock 数据 - 替代 Prisma 数据库查询，专注展示 Next.js 特性 */

export interface Customer {
  id: string
  name: string
  email: string
  image_url: string
  total_invoices: number
  total_pending: string
  total_paid: string
}

export interface Invoice {
  id: string
  customer_id: string
  name: string
  email: string
  image_url: string
  amount: number
  date: string
  status: "pending" | "paid"
}

export interface Revenue {
  month: string
  revenue: number
}

export const CUSTOMERS: Customer[] = [
  {
    id: "1",
    name: "Alice Johnson",
    email: "alice@example.com",
    image_url: "/images/mock/order/customers/alice.png",
    total_invoices: 5,
    total_pending: "$500.00",
    total_paid: "$2,000.00"
  },
  {
    id: "2",
    name: "Bob Smith",
    email: "bob@example.com",
    image_url: "/images/mock/order/customers/bob.png",
    total_invoices: 3,
    total_pending: "$200.00",
    total_paid: "$1,500.00"
  },
  {
    id: "3",
    name: "Carol White",
    email: "carol@example.com",
    image_url: "/images/mock/order/customers/carol.png",
    total_invoices: 8,
    total_pending: "$0.00",
    total_paid: "$4,200.00"
  },
  {
    id: "4",
    name: "David Brown",
    email: "david@example.com",
    image_url: "/images/mock/order/customers/david.png",
    total_invoices: 2,
    total_pending: "$800.00",
    total_paid: "$600.00"
  }
]

export const INVOICES: Invoice[] = [
  {
    id: "inv-1",
    customer_id: "1",
    name: "Alice Johnson",
    email: "alice@example.com",
    image_url: "/images/mock/order/customers/alice.png",
    amount: 500,
    date: "2024-01-15",
    status: "paid"
  },
  {
    id: "inv-2",
    customer_id: "2",
    name: "Bob Smith",
    email: "bob@example.com",
    image_url: "/images/mock/order/customers/bob.png",
    amount: 200,
    date: "2024-02-20",
    status: "pending"
  },
  {
    id: "inv-3",
    customer_id: "3",
    name: "Carol White",
    email: "carol@example.com",
    image_url: "/images/mock/order/customers/carol.png",
    amount: 1200,
    date: "2024-03-10",
    status: "paid"
  },
  {
    id: "inv-4",
    customer_id: "1",
    name: "Alice Johnson",
    email: "alice@example.com",
    image_url: "/images/mock/order/customers/alice.png",
    amount: 800,
    date: "2024-03-25",
    status: "pending"
  },
  {
    id: "inv-5",
    customer_id: "4",
    name: "David Brown",
    email: "david@example.com",
    image_url: "/images/mock/order/customers/david.png",
    amount: 350,
    date: "2024-04-05",
    status: "paid"
  },
  {
    id: "inv-6",
    customer_id: "2",
    name: "Bob Smith",
    email: "bob@example.com",
    image_url: "/images/mock/order/customers/bob.png",
    amount: 900,
    date: "2024-04-18",
    status: "paid"
  }
]

export const REVENUE: Revenue[] = [
  { month: "Jan", revenue: 2000 },
  { month: "Feb", revenue: 1800 },
  { month: "Mar", revenue: 2200 },
  { month: "Apr", revenue: 2600 },
  { month: "May", revenue: 2400 },
  { month: "Jun", revenue: 2800 },
  { month: "Jul", revenue: 3200 },
  { month: "Aug", revenue: 3000 },
  { month: "Sep", revenue: 2700 },
  { month: "Oct", revenue: 3100 },
  { month: "Nov", revenue: 3400 },
  { month: "Dec", revenue: 3800 }
]

/** 工具函数 */
export function formatCurrency(amount: number): string {
  return `$${(amount / 100).toFixed(2)}`
}

export function formatDateToLocal(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric"
  })
}

export function generateYAxis(revenue: Revenue[]): { yAxisLabels: string[]; topLabel: number } {
  const yAxisLabels = []
  const highestRecord = Math.max(...revenue.map((m) => m.revenue))
  const topLabel = Math.ceil(highestRecord / 1000) * 1000
  for (let i = topLabel; i >= 0; i -= 1000) {
    yAxisLabels.push(`$${i / 1000}K`)
  }
  return { yAxisLabels, topLabel }
}

export function generatePagination(currentPage: number, totalPages: number): (number | string)[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1)
  if (currentPage <= 3) return [1, 2, 3, "...", totalPages - 1, totalPages]
  if (currentPage >= totalPages - 2)
    return [1, 2, "...", totalPages - 2, totalPages - 1, totalPages]
  return [1, "...", currentPage - 1, currentPage, currentPage + 1, "...", totalPages]
}

/** 模拟异步数据查询（带延时，演示 Suspense 流式渲染效果） */
async function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function fetchLatestInvoices(): Promise<Invoice[]> {
  await delay(800)
  return INVOICES.slice(0, 5)
}

export async function fetchCardData() {
  await delay(1000) // 演示 Suspense fallback
  const paid = INVOICES.filter((i) => i.status === "paid")
  const pending = INVOICES.filter((i) => i.status === "pending")
  return {
    numberOfInvoices: INVOICES.length,
    numberOfCustomers: CUSTOMERS.length,
    totalPaidInvoices: formatCurrency(paid.reduce((sum, i) => sum + i.amount, 0) * 100),
    totalPendingInvoices: formatCurrency(pending.reduce((sum, i) => sum + i.amount, 0) * 100)
  }
}

export async function fetchRevenue(): Promise<Revenue[]> {
  await delay(3000) // 演示更长的加载时间（骨架屏效果）
  return REVENUE
}

export async function fetchFilteredInvoices(
  query: string,
  currentPage: number
): Promise<Invoice[]> {
  await delay(500)
  const filtered = query
    ? INVOICES.filter(
        (i) =>
          i.name.toLowerCase().includes(query.toLowerCase()) ||
          i.email.toLowerCase().includes(query.toLowerCase())
      )
    : INVOICES
  const perPage = 6
  return filtered.slice((currentPage - 1) * perPage, currentPage * perPage)
}

export async function fetchInvoicesPages(query: string): Promise<number> {
  const filtered = query
    ? INVOICES.filter(
        (i) =>
          i.name.toLowerCase().includes(query.toLowerCase()) ||
          i.email.toLowerCase().includes(query.toLowerCase())
      )
    : INVOICES
  return Math.ceil(filtered.length / 6) || 1
}

export async function fetchInvoiceById(id: string): Promise<Invoice | null> {
  return INVOICES.find((i) => i.id === id) ?? null
}

export async function fetchCustomers(query?: string): Promise<Customer[]> {
  if (!query) return CUSTOMERS
  const q = query.toLowerCase()
  return CUSTOMERS.filter(
    (c) => c.name.toLowerCase().includes(q) || c.email.toLowerCase().includes(q)
  )
}
