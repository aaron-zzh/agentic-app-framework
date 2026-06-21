"use client"

import type { ComponentPropsWithRef, RefObject } from "react"
import { Button } from "@/components/ui/button"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { cn } from "@/lib/utils/index"

export type TooltipIconButtonProps = ComponentPropsWithRef<typeof Button> & {
  tooltip: string
  side?: "top" | "bottom" | "left" | "right"
  ref?: RefObject<HTMLButtonElement | null>
}

export const TooltipIconButton = ({
  children,
  tooltip,
  side = "bottom",
  className,
  ref,
  ...rest
}: TooltipIconButtonProps) => {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              variant="ghost"
              size="icon"
              {...rest}
              className={cn("aui-button-icon size-6 p-1 active:scale-90", className)}
              ref={ref}
            />
          }
        >
          {children}
          <span className="aui-sr-only sr-only">{tooltip}</span>
        </TooltipTrigger>
        <TooltipContent side={side}>{tooltip}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

TooltipIconButton.displayName = "TooltipIconButton"
