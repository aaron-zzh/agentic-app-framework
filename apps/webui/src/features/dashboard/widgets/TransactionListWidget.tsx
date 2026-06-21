/**
 * TransactionListWidget——交易/积分流水列表。
 *
 * <p>同时支持两种数据形态：
 * <ul>
 *   <li>{@code tableData} — 旧 mock/通用形态</li>
 *   <li>{@code items} — 后端 billing-transactions 返回的真实流水行</li>
 * </ul>
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import { ArrowDownLeft, ArrowRight, ArrowUpRight, MoreVertical } from "lucide-react"
import Link from "next/link"
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
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"

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

/** 后端 billing-transactions 行 */
export interface BillingTransactionRow {
  id: number
  type: string // EARN / SPEND / FREEZE / UNFREEZE / EXPIRE
  amount: number
  balance_after: number
  biz_type: string | null
  batch_type: string | null
  create_time: string
  remark: string | null
}

interface TransactionListWidgetProps {
  title?: string
  tableData?: TransactionItem[]
  items?: BillingTransactionRow[]
}

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  completed: "default",
  progress: "secondary",
  failed: "destructive"
}

/** 流水 type 的中文显示 */
const TX_TYPE_LABEL: Record<string, string> = {
  EARN: "获取",
  SPEND: "消耗",
  FREEZE: "冻结",
  UNFREEZE: "解冻",
  EXPIRE: "过期"
}

/** biz_type / batch_type 的中文显示（最常见的） */
const TX_BIZ_LABEL: Record<string, string> = {
  AIGC_TASK: "AI 创作",
  AIGC_IMAGE: "图片生成",
  AIGC_VIDEO: "视频生成",
  TOOL_CALL_AUDIT: "工具调用",
  TOOL_CALL: "工具调用",
  CHAT: "AI 对话",
  AGENT: "智能体",
  COPYWRITING: "文案生成",
  SUBSCRIPTION: "订阅赠送",
  TOPUP: "充值",
  REWARD: "奖励",
  WEEKLY: "周积分",
  MANUAL: "管理员发放"
}

function formatCurrencyUSD(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

function formatDateTime(dateStr: string) {
  const d = new Date(dateStr)
  const date = d.toLocaleDateString("zh-CN", { month: "short", day: "numeric" })
  const time = d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false })
  return { date, time }
}

function isIncoming(type: string) {
  return type === "EARN" || type === "UNFREEZE"
}

export function TransactionListWidget({ title, tableData, items }: TransactionListWidgetProps) {
  const isBilling = !!items
  const hasData = isBilling ? (items?.length ?? 0) > 0 : (tableData?.length ?? 0) > 0

  return (
    <Card className={cn(WIDGET_CARD_CLASS)}>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-base">
          {title ?? (isBilling ? "积分流水" : "Transactions")}
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {!hasData ? (
          <div className="flex h-32 items-center justify-center text-muted-foreground text-sm">
            暂无流水数据
          </div>
        ) : isBilling ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>明细</TableHead>
                <TableHead>时间</TableHead>
                <TableHead>变动</TableHead>
                <TableHead>余额</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items?.map((row) => {
                const inflow = isIncoming(row.type)
                const dt = formatDateTime(row.create_time)
                const bizLabel =
                  TX_BIZ_LABEL[row.biz_type ?? ""] ?? TX_BIZ_LABEL[row.batch_type ?? ""] ?? ""
                const desc = row.remark || bizLabel || TX_TYPE_LABEL[row.type] || row.type
                return (
                  <TableRow key={row.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <div className="relative">
                          <Avatar className="h-10 w-10">
                            <AvatarFallback className="bg-muted text-base">
                              {desc.charAt(0)}
                            </AvatarFallback>
                          </Avatar>
                          <span
                            className={cn(
                              "absolute -right-0.5 -bottom-0.5 flex h-4 w-4 items-center justify-center rounded-full text-white",
                              inflow ? "bg-emerald-500" : "bg-orange-500"
                            )}
                          >
                            {inflow ? (
                              <ArrowDownLeft className="h-2.5 w-2.5" />
                            ) : (
                              <ArrowUpRight className="h-2.5 w-2.5" />
                            )}
                          </span>
                        </div>
                        <div>
                          <p className="font-medium text-sm">{desc}</p>
                          <p className="text-muted-foreground text-xs">
                            {TX_TYPE_LABEL[row.type] ?? row.type}
                            {bizLabel ? ` · ${bizLabel}` : ""}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <p className="text-sm">{dt.date}</p>
                      <p className="text-muted-foreground text-xs">{dt.time}</p>
                    </TableCell>
                    <TableCell
                      className={cn("font-medium", inflow ? "text-emerald-600" : "text-orange-600")}
                    >
                      {inflow ? "+" : "-"}
                      {row.amount} 积分
                    </TableCell>
                    <TableCell className="text-muted-foreground text-sm">
                      {row.balance_after}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
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
              {tableData?.map((row) => (
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
                  <TableCell>{row.date}</TableCell>
                  <TableCell
                    className={`font-medium ${row.amount >= 0 ? "text-green-600" : "text-red-500"}`}
                  >
                    {formatCurrencyUSD(row.amount)}
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
        )}
      </CardContent>
      <Separator />
      <div className="flex justify-end p-2">
        <Button
          variant="ghost"
          size="sm"
          className="gap-1 text-xs"
          nativeButton={false}
          render={<Link href="/module/wallet-transaction" />}
        >
          查看全部 <ArrowRight className="h-3 w-3" />
        </Button>
      </div>
    </Card>
  )
}
