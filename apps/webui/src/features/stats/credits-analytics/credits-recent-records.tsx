/**
 * 积分流水记录表格（管理员视角）
 * 数据来源：GET /api/stats/credits/records
 * @author AaronZZH
 */

"use client"

import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import type { CreditRecordVO } from "@/lib/api/rest/dashboard/credits-analytics"
import { useCreditsRecords } from "@/lib/queries/use-credits-analytics"

const PAGE_SIZE = 10

const TYPE_LABEL: Record<CreditRecordVO["type"], string> = {
  EARN: "收入",
  SPEND: "消耗",
  FREEZE: "冻结",
  UNFREEZE: "解冻",
  EXPIRE: "过期"
}

const TYPE_VARIANT: Record<
  CreditRecordVO["type"],
  "default" | "secondary" | "destructive" | "outline"
> = {
  EARN: "default",
  SPEND: "secondary",
  FREEZE: "outline",
  UNFREEZE: "outline",
  EXPIRE: "destructive"
}

function formatTime(iso: string) {
  return iso.replace("T", " ").slice(0, 16)
}

export function CreditsRecentRecords() {
  const [pageNo, setPageNo] = useState(1)
  const { data, isLoading } = useCreditsRecords(pageNo, PAGE_SIZE)

  const records = data?.list ?? []
  const total = data?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>最近消耗记录</CardTitle>
        <p className="text-muted-foreground text-sm">共 {total.toLocaleString()} 条积分流水</p>
      </CardHeader>
      <CardContent className="p-0">
        <ScrollArea className="min-h-[340px]">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>用户 / 手机</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>分类</TableHead>
                <TableHead className="text-right">积分</TableHead>
                <TableHead className="text-right">余额</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading
                ? Array.from({ length: PAGE_SIZE }).map((_, i) => (
                    <TableRow key={i}>
                      {Array.from({ length: 7 }).map((__, j) => (
                        <TableCell key={j}>
                          <Skeleton className="h-4 w-full" />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : records.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>
                        <div className="font-medium">{row.userName ?? "—"}</div>
                        <div className="text-muted-foreground text-xs">{row.phone ?? "—"}</div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={TYPE_VARIANT[row.type]}>{TYPE_LABEL[row.type]}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm">
                        {row.category ?? "—"}
                      </TableCell>
                      <TableCell className="text-right font-medium font-mono">
                        {row.type === "EARN" ? "+" : "-"}
                        {row.amount.toLocaleString()}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm">
                        {row.balanceAfter.toLocaleString()}
                      </TableCell>
                      <TableCell className="max-w-[120px] truncate text-muted-foreground text-sm">
                        {row.remark ?? row.source ?? "—"}
                      </TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {formatTime(row.createTime)}
                      </TableCell>
                    </TableRow>
                  ))}
            </TableBody>
          </Table>
        </ScrollArea>
      </CardContent>
      <CardFooter className="flex items-center justify-between border-t p-3">
        <span className="text-muted-foreground text-xs">
          第 {pageNo} / {totalPages || 1} 页
        </span>
        <div className="flex gap-2">
          <Button
            variant="ghost"
            size="sm"
            disabled={pageNo <= 1}
            onClick={() => setPageNo((p) => p - 1)}
          >
            上一页
          </Button>
          <Button
            variant="ghost"
            size="sm"
            disabled={pageNo >= totalPages}
            onClick={() => setPageNo((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      </CardFooter>
    </Card>
  )
}
