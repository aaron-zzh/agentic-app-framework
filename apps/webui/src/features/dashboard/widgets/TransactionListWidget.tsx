/**
 * TransactionListWidget——交易记录表格（支持收入/支出、状态、操作菜单）
 */

"use client"

import { ArrowDownLeft, ArrowRight, ArrowUpRight, MoreVertical } from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Separator } from "@/components/ui/separator"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"

export interface TransactionItem {
  id: string
  type: string
  status: string
  amount: number
  message: string
  category: string
  date: string
  name: string | null
  avatarUrl: string | null
}

interface TransactionListWidgetProps {
  title?: string
  tableData: TransactionItem[]
}

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  completed: "default",
  progress: "secondary",
  failed: "destructive"
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric"
  })
}

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })
}

export function TransactionListWidget({ title, tableData }: TransactionListWidgetProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Description</TableHead>
              <TableHead>Date</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {tableData.map((row) => (
              <TableRow key={row.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <div className="relative">
                      <Avatar className="h-10 w-10">
                        <AvatarFallback className="bg-muted text-base">
                          {row.message.charAt(0)}
                        </AvatarFallback>
                      </Avatar>
                      <span
                        className={`absolute -right-0.5 -bottom-0.5 flex h-4 w-4 items-center justify-center rounded-full text-white ${row.type === "Income" ? "bg-green-500" : "bg-red-500"}`}
                      >
                        {row.type === "Income" ? (
                          <ArrowDownLeft className="h-2.5 w-2.5" />
                        ) : (
                          <ArrowUpRight className="h-2.5 w-2.5" />
                        )}
                      </span>
                    </div>
                    <div>
                      <p className="font-medium text-sm">{row.message}</p>
                      <p className="text-muted-foreground text-xs">{row.category}</p>
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <p className="text-sm">{formatDate(row.date)}</p>
                  <p className="text-muted-foreground text-xs">{formatTime(row.date)}</p>
                </TableCell>
                <TableCell
                  className={`font-medium ${row.amount >= 0 ? "text-green-600" : "text-red-500"}`}
                >
                  {formatCurrency(row.amount)}
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[row.status] ?? "outline"} className="capitalize">
                    {row.status}
                  </Badge>
                </TableCell>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger className="flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent">
                      <MoreVertical className="h-4 w-4" />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem>Download</DropdownMenuItem>
                      <DropdownMenuItem>Print</DropdownMenuItem>
                      <DropdownMenuItem>Share</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem className="text-destructive">Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
      <Separator />
      <div className="flex justify-end p-2">
        <Button variant="ghost" size="sm" className="gap-1 text-xs">
          View all <ArrowRight className="h-3 w-3" />
        </Button>
      </div>
    </Card>
  )
}
