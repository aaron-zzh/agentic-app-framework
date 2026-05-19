/**
 * FAQSection — 常见问题折叠面板（shadcn Accordion）
 * @author AaronZZH & Kiro
 */

import {
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from "@/components/ui/accordion"

import type { SectionComponentProps } from "../types"

interface FAQItem {
  question: string
  answer: string
}

interface FAQProps {
  title?: string
  subtitle?: string
  items?: FAQItem[]
}

/** 常见问题 Section */
export function FAQSection({ data }: SectionComponentProps) {
  const { title, subtitle, items = [] } = data as FAQProps

  return (
    <section className="w-full px-6 py-16 md:py-24">
      <div className="mx-auto max-w-3xl">
        {/* 标题区 */}
        {(title || subtitle) && (
          <div className="mb-12 text-center">
            {title && <h2 className="font-bold text-3xl">{title}</h2>}
            {subtitle && <p className="mt-3 text-muted-foreground">{subtitle}</p>}
          </div>
        )}

        {/* 折叠面板 */}
        <Accordion>
          {(items as FAQItem[]).map((item, i) => (
            <AccordionItem key={item.question} value={`faq-${i}`}>
              <AccordionTrigger>{item.question}</AccordionTrigger>
              <AccordionContent>
                <p className="text-muted-foreground">{item.answer}</p>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </div>
    </section>
  )
}
