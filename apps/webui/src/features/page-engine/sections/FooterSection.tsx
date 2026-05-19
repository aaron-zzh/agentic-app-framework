/**
 * FooterSection — 页脚（链接分组 + 社交媒体 + 版权）
 * @author AaronZZH & Kiro
 */

import { Separator } from "@/components/ui/separator"

import type { SectionComponentProps } from "../types"

interface FooterLink {
  label: string
  href: string
}

interface FooterGroup {
  title: string
  links: FooterLink[]
}

interface SocialLink {
  name: string
  href: string
  icon?: string
}

interface FooterProps {
  groups?: FooterGroup[]
  social?: SocialLink[]
  copyright?: string
  logo?: string
  logoText?: string
}

/** 页脚 Section */
export function FooterSection({ data }: SectionComponentProps) {
  const {
    groups = [],
    social = [],
    copyright = `© ${new Date().getFullYear()} All rights reserved.`,
    logoText = "AAF",
  } = data as FooterProps

  return (
    <footer className="w-full bg-muted/30 pt-12 pb-8">
      <div className="mx-auto max-w-7xl px-6">
        {/* 链接分组 */}
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          {(groups as FooterGroup[]).map((group) => (
            <div key={group.title}>
              <h4 className="mb-3 font-semibold text-sm">{group.title}</h4>
              <ul className="flex flex-col gap-2">
                {group.links.map((link) => (
                  <li key={link.href}>
                    <a
                      href={link.href}
                      className="text-muted-foreground text-sm transition-colors hover:text-foreground"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <Separator className="my-8" />

        {/* 底部：版权 + 社交媒体 */}
        <div className="flex flex-col items-center justify-between gap-4 md:flex-row">
          <div className="flex items-center gap-2">
            <span className="font-bold">{logoText}</span>
            <span className="text-muted-foreground text-sm">{copyright}</span>
          </div>

          {(social as SocialLink[]).length > 0 && (
            <div className="flex items-center gap-4">
              {(social as SocialLink[]).map((item) => (
                <a
                  key={item.href}
                  href={item.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={item.name}
                >
                  {item.name}
                </a>
              ))}
            </div>
          )}
        </div>
      </div>
    </footer>
  )
}
