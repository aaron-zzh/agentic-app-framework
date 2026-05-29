/**
 * ShortcutWidget——快捷入口卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { Zap } from "lucide-react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { ShortcutWidgetConfig } from "@/lib/api/dashboard"

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
            <Button key={item.href} variant="outline" size="sm" asChild className="justify-start">
              <Link href={item.href}>{item.label}</Link>
            </Button>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
