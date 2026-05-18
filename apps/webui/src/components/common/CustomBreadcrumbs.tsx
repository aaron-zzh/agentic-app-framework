/**
 * CustomBreadcrumbs——面包屑 + 标题 + 右侧操作区
 * @author AaronZZH & Kiro
 */

import Link from "next/link"
import { Fragment } from "react"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator
} from "@/components/ui/breadcrumb"
import { cn } from "@/lib/utils/cn"

export interface BreadcrumbLinkItem {
  name: string
  href?: string
}

interface CustomBreadcrumbsProps {
  heading?: string
  links?: BreadcrumbLinkItem[]
  action?: React.ReactNode
  className?: string
}

export function CustomBreadcrumbs({
  heading,
  links = [],
  action,
  className
}: CustomBreadcrumbsProps) {
  return (
    <div className={cn("flex items-center justify-between", className)}>
      <div className="space-y-1">
        {heading && <h4 className="font-semibold text-lg tracking-tight">{heading}</h4>}
        {links.length > 0 && (
          <Breadcrumb>
            <BreadcrumbList>
              {links.map((link, i) => {
                const isLast = i === links.length - 1
                if (isLast || !link.href) {
                  return (
                    <BreadcrumbItem key={`${link.name}-${link.href ?? i}`}>
                      <BreadcrumbPage>{link.name}</BreadcrumbPage>
                    </BreadcrumbItem>
                  )
                }
                return (
                  <Fragment key={`${link.name}-${link.href}`}>
                    <BreadcrumbItem>
                      <BreadcrumbLink render={<Link href={link.href} />}>
                        {link.name}
                      </BreadcrumbLink>
                    </BreadcrumbItem>
                    <BreadcrumbSeparator />
                  </Fragment>
                )
              })}
            </BreadcrumbList>
          </Breadcrumb>
        )}
      </div>
      {action && <div>{action}</div>}
    </div>
  )
}
