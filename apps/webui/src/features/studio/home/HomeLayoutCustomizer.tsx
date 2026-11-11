/**
 * Studio 首页"自定义"Popover：逐个面板开关显示、"恢复默认"，底部预留禁用的"添加图表（即将开放）"。
 *
 * 偏好只存本地。中央画布始终显示，不在列表中。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChartNoAxesCombined, Settings2 } from "lucide-react"
import { useId } from "react"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger
} from "@/components/ui/popover"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { useStudioHomeLayoutStore } from "@/lib/store/studio-home-layout-store"
import { cn } from "@/lib/utils/cn"
import {
  HOME_WIDGET_REGION_LABELS,
  HOME_WIDGETS,
  resolveHomeWidgetVisibility
} from "./home-widgets"

export function HomeLayoutCustomizer() {
  const uid = useId()
  const hiddenKeys = useStudioHomeLayoutStore((state) => state.hiddenKeys)
  const collapsedKeys = useStudioHomeLayoutStore((state) => state.collapsedKeys)
  const bottomCollapsed = useStudioHomeLayoutStore((state) => state.bottomCollapsed)
  const setWidgetHidden = useStudioHomeLayoutStore((state) => state.setWidgetHidden)
  const resetLayout = useStudioHomeLayoutStore((state) => state.resetLayout)
  const visibility = resolveHomeWidgetVisibility(hiddenKeys)
  const isDefault = hiddenKeys.length === 0 && collapsedKeys.length === 0 && !bottomCollapsed

  return (
    <Popover>
      <PopoverTrigger
        className={cn(
          buttonVariants({ variant: "outline", size: "sm" }),
          "bg-background/50 backdrop-blur-md"
        )}
      >
        <Settings2 />
        自定义
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64">
        <PopoverHeader>
          <PopoverTitle>首页面板</PopoverTitle>
          <PopoverDescription>选择要在首页显示的面板，中央画布始终显示。</PopoverDescription>
        </PopoverHeader>
        <ul className="flex flex-col gap-1">
          {HOME_WIDGETS.map((widget) => {
            const switchId = `${uid}-${widget.key}`
            return (
              <li key={widget.key} className="flex items-center justify-between gap-3 py-1">
                <label
                  htmlFor={switchId}
                  className="flex flex-1 cursor-pointer items-baseline gap-2"
                >
                  <span className="text-sm">{widget.title}</span>
                  <span className="text-muted-foreground text-xs">
                    {HOME_WIDGET_REGION_LABELS[widget.region]}
                  </span>
                </label>
                <Switch
                  id={switchId}
                  size="sm"
                  checked={visibility[widget.key]}
                  onCheckedChange={(checked) => setWidgetHidden(widget.key, !checked)}
                />
              </li>
            )
          })}
        </ul>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="self-end"
          disabled={isDefault}
          onClick={resetLayout}
        >
          恢复默认
        </Button>
        <Separator />
        {/* 预留：以后用户可自行往大屏添加图表，本期不实现 */}
        <Button type="button" variant="outline" size="sm" disabled>
          <ChartNoAxesCombined />
          添加图表（即将开放）
        </Button>
      </PopoverContent>
    </Popover>
  )
}
