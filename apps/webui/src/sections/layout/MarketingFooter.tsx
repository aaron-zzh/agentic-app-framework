/**
 * MarketingFooter——营销页页脚
 * @author AaronZZH & Kiro
 */

import Link from "next/link"

const footerGroups = [
  {
    title: "产品",
    links: [
      { label: "功能", href: "/#features" },
      { label: "定价", href: "/pricing" },
      { label: "模板市场", href: "/templates" }
    ]
  },
  {
    title: "资源",
    links: [
      { label: "文档", href: "/docs" },
      { label: "API 参考", href: "/docs/api" },
      { label: "更新日志", href: "/changelog" }
    ]
  },
  {
    title: "关于",
    links: [
      { label: "团队", href: "/about" },
      { label: "博客", href: "/blog" },
      { label: "联系我们", href: "/contact" }
    ]
  }
]

/** 营销页页脚 */
export function MarketingFooter() {
  return (
    <footer className="border-t bg-muted/30">
      <div className="mx-auto max-w-[var(--layout-marketing-max-width)] px-6 py-12">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          <div>
            <span className="text-lg font-bold">AAF</span>
            <p className="mt-2 text-sm text-muted-foreground">AI 原生应用开发框架</p>
          </div>
          {footerGroups.map((group) => (
            <div key={group.title}>
              <p className="text-sm font-medium">{group.title}</p>
              <ul className="mt-2 space-y-1.5">
                {group.links.map((link) => (
                  <li key={link.href}>
                    <Link href={link.href} className="text-sm text-muted-foreground hover:text-foreground">
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-8 border-t pt-6 text-center text-xs text-muted-foreground">
          © {new Date().getFullYear()} AAF. All rights reserved.
        </div>
      </div>
    </footer>
  )
}
