/**
 * 特性演示：usePathname 高亮活跃导航项
 *
 * 客户端组件（'use client'）读取当前路径，动态添加活跃样式。
 * 使用 next-view-transitions 的 Link 替代 next/link，触发过渡动画。
 */
"use client"

import clsx from "clsx"
import {
  Copy as DocumentDuplicateIcon,
  Home as HomeIcon,
  Users as UserGroupIcon
} from "lucide-react"
import { usePathname } from "next/navigation"
import { Link } from "next-view-transitions"

const links = [
  { name: "Home", href: "/examples/nextjs-features/dashboard", icon: HomeIcon },
  {
    name: "Invoices",
    href: "/examples/nextjs-features/dashboard/invoices",
    icon: DocumentDuplicateIcon
  },
  { name: "Customers", href: "/examples/nextjs-features/dashboard/customers", icon: UserGroupIcon }
]

export default function NavLinks() {
  const pathname = usePathname()

  return (
    <>
      {links.map((link) => {
        const Icon = link.icon
        return (
          <Link
            key={link.name}
            href={link.href}
            className={clsx(
              "flex h-10 grow items-center justify-center gap-2 rounded-md bg-gray-50 p-3 font-medium text-sm hover:bg-sky-100 hover:text-blue-600 md:flex-none md:justify-start md:p-2 md:px-3",
              { "bg-sky-100 text-blue-600": pathname === link.href }
            )}
          >
            <Icon className="w-4" />
            <p className="hidden md:block">{link.name}</p>
          </Link>
        )
      })}
    </>
  )
}
