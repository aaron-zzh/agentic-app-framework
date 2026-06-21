/**
 * ShortcutWidget——快捷入口卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { Zap } from "lucide-react"
import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { ShortcutWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"

interface ShortcutWidgetProps {
  title: string
  config: ShortcutWidgetConfig
}

export function ShortcutWidget({ title, config }: ShortcutWidgetProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 font-medium text-sm">
          <Zap className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-2">
          {config.items.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={buttonVariants({
                variant: "outline",
                size: "sm",
                className: "justify-start"
              })}
            >
              {item.label}
            </Link>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
