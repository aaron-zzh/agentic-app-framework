/**
 * PricingFAQ——pricing 页常见问题（用 shadcn Accordion）
 *
 * 替换原手写按钮 accordion，获得 a11y（aria-expanded、键盘导航）+ 一致动效。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"

export interface FaqItem {
  q: string
  a: string
}

export interface PricingFAQProps {
  items: FaqItem[]
}

export function PricingFAQ({ items }: PricingFAQProps) {
  if (items.length === 0) return null

  return (
    <Accordion>
      {items.map((item, i) => (
        <AccordionItem key={item.q} value={`faq-${i}`}>
          <AccordionTrigger className="px-5 py-4 text-base">
            <span className="flex items-baseline gap-3">
              <span className="font-mono text-muted-foreground text-xs">
                {String(i + 1).padStart(2, "0")}
              </span>
              {item.q}
            </span>
          </AccordionTrigger>
          <AccordionContent className="px-5">
            <p className="whitespace-pre-line text-muted-foreground">{item.a}</p>
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  )
}
