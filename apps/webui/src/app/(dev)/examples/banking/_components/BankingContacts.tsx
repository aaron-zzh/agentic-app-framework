/**
 * BankingContacts——联系人列表
 */

"use client"

import { ArrowRight, ArrowRightLeft } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"

interface Contact {
  id: string
  name: string
  email: string
  avatarUrl: string
}

interface BankingContactsProps {
  title?: string
  subheader?: string
  list: Contact[]
}

export function BankingContacts({ title, subheader, list }: BankingContactsProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <div>
          <CardTitle className="text-base">{title}</CardTitle>
          {subheader && <p className="text-muted-foreground text-sm">{subheader}</p>}
        </div>
        <Button variant="ghost" size="sm" className="gap-1 text-xs">
          View all <ArrowRight className="h-3 w-3" />
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        {list.map((item) => (
          <div key={item.id} className="flex items-center gap-3">
            <Avatar>
              <AvatarImage src={item.avatarUrl} />
              <AvatarFallback>{item.name.charAt(0)}</AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-sm">{item.name}</p>
              <p className="truncate text-muted-foreground text-xs">{item.email}</p>
            </div>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger>
                  <Button variant="ghost" size="icon" className="h-8 w-8 shrink-0">
                    <ArrowRightLeft className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Quick transfer</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
