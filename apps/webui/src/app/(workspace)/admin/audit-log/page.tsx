/**
 * 审计日志列表页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH1 } from "@/components/ui/typography"
import type { AuditLogListParams } from "@/lib/api/audit-log"
import { useAuditLogList } from "@/lib/queries/use-audit-log"

/** 操作类型标签颜色 */
const ACTION_VARIANT: Record<string, "default" | "secondary" | "destructive"> = {
  create: "default",
  update: "secondary",
  delete: "destructive"
}

const ACTION_LABEL: Record<string, string> = {
  create: "创建",
  update: "更新",
  delete: "删除"
}

export default function AuditLogPage() {
  const [params, setParams] = useState<AuditLogListParams>({ page: 1, pageSize: 20 })
  const { data, isLoading } = useAuditLogList(params)

  function updateFilter(key: keyof AuditLogListParams, value: string) {
    setParams((prev) => ({ ...prev, [key]: value || undefined, page: 1 }))
  }

  return (
    <PageContainer>
      <TypographyH1 className="mb-6">审计日志</TypographyH1>

      {/* 筛选栏 */}
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          placeholder="实体类型"
          className="w-40"
          value={params.entityType ?? ""}
          onChange={(e) => updateFilter("entityType", e.target.value)}
        />
        <Input
          placeholder="操作人 ID"
          className="w-40"
          value={params.userId ?? ""}
          onChange={(e) => updateFilter("userId", e.target.value)}
        />
        <Select
          value={params.action ?? ""}
          onValueChange={(v) => updateFilter("action", v)}
        >
          <SelectTrigger className="w-32">
            <SelectValue placeholder="操作类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="create">创建</SelectItem>
            <SelectItem value="update">更新</SelectItem>
            <SelectItem value="delete">删除</SelectItem>
          </SelectContent>
        </Select>
        <Input
          type="date"
          className="w-40"
          value={params.startTime ?? ""}
          onChange={(e) => updateFilter("startTime", e.target.value)}
        />
        <Input
          type="date"
          className="w-40"
          value={params.endTime ?? ""}
          onChange={(e) => updateFilter("endTime", e.target.value)}
        />
        <Button
          variant="outline"
          onClick={() => setParams({ page: 1, pageSize: 20 })}
        >
          重置
        </Button>
      </div>

      {/* 列表 */}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-40">时间</TableHead>
            <TableHead>操作人</TableHead>
            <TableHead>实体</TableHead>
            <TableHead>记录 ID</TableHead>
            <TableHead>操作</TableHead>
            <TableHead>IP</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && (
            <TableRow>
              <TableCell colSpan={6} className="text-center text-muted-foreground">
                加载中...
              </TableCell>
            </TableRow>
          )}
          {data?.list.map((log) => (
            <TableRow key={log.id} className="group">
              <TableCell colSpan={6} className="p-0">
                <Accordion>
                  <AccordionItem value={log.id} className="border-none">
                    <AccordionTrigger className="px-4 py-3 hover:no-underline">
                      <div className="flex w-full items-center gap-4 text-sm">
                        <span className="w-40 shrink-0 text-muted-foreground">
                          {new Date(log.createdAt).toLocaleString("zh-CN")}
                        </span>
                        <span className="w-24 shrink-0">{log.userId}</span>
                        <span className="w-24 shrink-0">{log.entityType}</span>
                        <span className="w-24 shrink-0 truncate">{log.entityId}</span>
                        <Badge variant={ACTION_VARIANT[log.action] ?? "default"}>
                          {ACTION_LABEL[log.action] ?? log.action}
                        </Badge>
                        <span className="text-muted-foreground">{log.ip}</span>
                      </div>
                    </AccordionTrigger>
                    <AccordionContent className="px-4 pb-3">
                      {log.changes.length === 0 ? (
                        <span className="text-muted-foreground">无字段变更详情</span>
                      ) : (
                        <div className="space-y-1">
                          {log.changes.map((c) => (
                            <div key={c.field} className="flex items-center gap-2 text-sm">
                              <span className="font-medium w-32">{c.field}</span>
                              <span className="text-red-600 line-through">
                                {c.oldValue || "(空)"}
                              </span>
                              <span className="text-muted-foreground">→</span>
                              <span className="text-green-600">
                                {c.newValue || "(空)"}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </AccordionContent>
                  </AccordionItem>
                </Accordion>
              </TableCell>
            </TableRow>
          ))}
          {!isLoading && data?.list.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="text-center text-muted-foreground">
                暂无审计日志
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      {/* 分页 */}
      {data && data.total > (params.pageSize ?? 20) && (
        <div className="mt-4 flex items-center justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={(params.page ?? 1) <= 1}
            onClick={() => setParams((p) => ({ ...p, page: (p.page ?? 1) - 1 }))}
          >
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">
            第 {params.page ?? 1} 页 / 共 {Math.ceil(data.total / (params.pageSize ?? 20))} 页
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={(params.page ?? 1) >= Math.ceil(data.total / (params.pageSize ?? 20))}
            onClick={() => setParams((p) => ({ ...p, page: (p.page ?? 1) + 1 }))}
          >
            下一页
          </Button>
        </div>
      )}
    </PageContainer>
  )
}
