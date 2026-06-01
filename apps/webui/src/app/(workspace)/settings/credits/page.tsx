/**
 * 积分概览页面——显示余额、流水列表、充值入口
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import type { CreditTransactionType } from "@/lib/api/rest/billing/credits"
import { useCreditBalance, useCreditTransactions } from "@/lib/queries/use-credits"
import { CreditRechargeDialog } from "@/components/common/CreditRechargeDialog"

/** 流水类型标签 */
const TYPE_LABEL: Record<CreditTransactionType, string> = {
  EARN: "获得",
  SPEND: "消费",
  FREEZE: "冻结",
  UNFREEZE: "解冻",
  EXPIRE: "过期"
}

/** 流水类型颜色 */
const TYPE_VARIANT: Record<CreditTransactionType, "default" | "secondary" | "destructive"> = {
  EARN: "default",
  SPEND: "destructive",
  FREEZE: "secondary",
  UNFREEZE: "default",
  EXPIRE: "secondary"
}

export default function CreditsPage() {
  const [page, setPage] = useState(0)
  const [rechargeOpen, setRechargeOpen] = useState(false)
  const pageSize = 20
  const { data: balance, isLoading: balanceLoading } = useCreditBalance()
  const { data: transactions, isLoading: txLoading } = useCreditTransactions(page, pageSize)

  if (balanceLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      {/* 页面标题 + 充值按钮 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-semibold text-2xl">积分管理</h1>
          <p className="text-muted-foreground text-sm">查看积分余额和消费记录</p>
        </div>
        <Button onClick={() => setRechargeOpen(true)}>充值</Button>
      </div>

      <CreditRechargeDialog open={rechargeOpen} onOpenChange={setRechargeOpen} />

      {/* 余额卡片 */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>可用积分</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="font-bold text-2xl">{balance?.balance ?? 0}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>冻结积分</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="font-bold text-2xl text-muted-foreground">{balance?.frozen ?? 0}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>累计获得</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="font-bold text-2xl text-green-600">{balance?.totalEarned ?? 0}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>累计消费</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="font-bold text-2xl text-red-600">{balance?.totalSpent ?? 0}</p>
          </CardContent>
        </Card>
      </div>

      {/* 流水列表 */}
      <Card>
        <CardHeader>
          <CardTitle>积分流水</CardTitle>
          <CardDescription>最近的积分变动记录</CardDescription>
        </CardHeader>
        <CardContent>
          {txLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={`sk-${i}`} className="h-10 w-full" />
              ))}
            </div>
          ) : !transactions?.list.length ? (
            <p className="py-8 text-center text-muted-foreground text-sm">暂无流水记录</p>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>类型</TableHead>
                    <TableHead>金额</TableHead>
                    <TableHead>余额</TableHead>
                    <TableHead>来源</TableHead>
                    <TableHead>时间</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {transactions.list.map((tx) => (
                    <TableRow key={tx.id}>
                      <TableCell>
                        <Badge variant={TYPE_VARIANT[tx.type]}>{TYPE_LABEL[tx.type]}</Badge>
                      </TableCell>
                      <TableCell className={tx.amount >= 0 ? "text-green-600" : "text-red-600"}>
                        {tx.amount >= 0 ? `+${tx.amount}` : tx.amount}
                      </TableCell>
                      <TableCell>{tx.balanceAfter}</TableCell>
                      <TableCell className="text-muted-foreground text-sm">{tx.source}</TableCell>
                      <TableCell className="text-muted-foreground text-sm">
                        {tx.createTime}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* 分页 */}
              {transactions.total > pageSize && (
                <div className="flex items-center justify-end gap-2 pt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    上一页
                  </Button>
                  <span className="text-muted-foreground text-sm">
                    第 {page + 1} / {Math.ceil(transactions.total / pageSize)} 页
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={(page + 1) * pageSize >= transactions.total}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    下一页
                  </Button>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
