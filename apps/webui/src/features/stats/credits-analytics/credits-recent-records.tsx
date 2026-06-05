/**
 * 积分最近消耗记录表格
 * @author Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { type CreditRecord, MOCK_RECENT_RECORDS } from "./_mock"

const STATUS_VARIANT: Record<CreditRecord["status"], "default" | "secondary" | "destructive"> = {
  成功: "default",
  处理中: "secondary",
  失败: "destructive"
}

export function CreditsRecentRecords() {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>最近消耗记录</CardTitle>
        <p className="text-muted-foreground text-sm">
          最近 {MOCK_RECENT_RECORDS.length} 条积分消耗流水
        </p>
      </CardHeader>
      <CardContent className="p-0">
        <ScrollArea className="min-h-[340px]">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>用户 / 部门</TableHead>
                <TableHead>服务</TableHead>
                <TableHead>模型</TableHead>
                <TableHead className="text-right">消耗积分</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {MOCK_RECENT_RECORDS.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <div className="font-medium">{row.user}</div>
                    <div className="text-muted-foreground text-xs">{row.dept}</div>
                  </TableCell>
                  <TableCell className="text-sm">{row.service}</TableCell>
                  <TableCell className="text-muted-foreground text-sm">{row.model}</TableCell>
                  <TableCell className="text-right font-medium font-mono">
                    {row.amount.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <Badge variant={STATUS_VARIANT[row.status]}>{row.status}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{row.time}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </ScrollArea>
      </CardContent>
      <CardFooter className="justify-end border-t p-3">
        <Button variant="ghost" size="sm">
          查看全部 →
        </Button>
      </CardFooter>
    </Card>
  )
}
