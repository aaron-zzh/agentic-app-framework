/**
 * LogosSection — 客户/合作伙伴 Logo 墙
 * @author AaronZZH & Kiro
 */

import type { SectionComponentProps } from "../types"

interface LogoItem {
  name: string
  logo: string
  url?: string
}

interface LogosProps {
  title?: string
  items?: LogoItem[]
}

/** 客户 Logo 墙 Section */
export function LogosSection({ data }: SectionComponentProps) {
  const { title, items = [] } = data as LogosProps

  return (
    <section className="w-full px-6 py-12">
      <div className="mx-auto max-w-7xl">
        {title && <p className="mb-8 text-center text-muted-foreground text-sm">{title}</p>}

        <div className="flex flex-wrap items-center justify-center gap-8 md:gap-12">
          {(items as LogoItem[]).map((item) => {
            const img = (
              // biome-ignore lint/performance/noImgElement: 动态用户配置 Logo 图片
              <img
                src={item.logo}
                alt={item.name}
                className="h-8 max-w-[120px] object-contain opacity-60 grayscale transition-all hover:opacity-100 hover:grayscale-0"
              />
            )

            if (item.url) {
              return (
                <a key={item.name} href={item.url} target="_blank" rel="noopener noreferrer">
                  {img}
                </a>
              )
            }
            return <div key={item.name}>{img}</div>
          })}
        </div>
      </div>
    </section>
  )
}
