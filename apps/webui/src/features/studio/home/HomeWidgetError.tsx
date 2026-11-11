/**
 * Studio 首页卡片失败态：只影响当前卡片，提供重试。
 * @author AaronZZH & Kiro
 */

import { RefreshCw, TriangleAlert } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"

interface HomeWidgetErrorProps {
  title: string
  onRetry: () => void
}

export function HomeWidgetError({ title, onRetry }: HomeWidgetErrorProps) {
  return (
    <Empty className="min-h-44 flex-1 p-4">
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <TriangleAlert />
        </EmptyMedia>
        <EmptyTitle>{title}</EmptyTitle>
        <EmptyDescription>其他卡片不受影响。</EmptyDescription>
      </EmptyHeader>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        <RefreshCw data-icon="inline-start" />
        重试
      </Button>
    </Empty>
  )
}
