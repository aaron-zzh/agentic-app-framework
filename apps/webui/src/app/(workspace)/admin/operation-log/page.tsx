/**
 * 操作日志列表页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { TablePagination } from "@/components/table/TablePagination"
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
import type { OperationLogListParams } from "@/lib/api/rest/admin/operation-log"
import { useOperationLogList } from "@/lib/queries/use-operation-log"

const TYPE_LABEL: Record<string, string> = {
  CREATE: "创建",
  UPDATE: "更新",
  DELETE: "删除",
  QUERY: "查询",
  EXPORT: "导出",
  IMPORT: "导入",
  LOGIN: "登录",
  OTHER: "其他"
}

export default function OperationLogPage() {
  const [params, setParams] = useState<OperationLogListParams>({ page: 1, pageSize: 20 })
  const { data, isLoading } = useOperationLogList(params)

  function updateFilter(key: keyof OperationLogListParams, value: string) {
    setParams((prev) => ({ ...prev, [key]: value || undefined, page: 1 }))
  }

  return (
    <PageContainer>
      <TypographyH1 className="mb-6">操作日志</TypographyH1>

      {/* 筛选栏 */}
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          placeholder="模块"
          className="w-32"
          value={params.module ?? ""}
          onChange={(e) => updateFilter("module", e.target.value)}
        />
        <Select value={params.type ?? ""} onValueChange={(v) => updateFilter("type", v ?? "")}>
          <SelectTrigger className="w-32">
            <SelectValue placeholder="操作类型" />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(TYPE_LABEL).map(([v, l]) => (
              <SelectItem key={v} value={v}>
                {l}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          placeholder="用户 ID"
          className="w-32"
          value={params.userId ?? ""}
          onChange={(e) => updateFilter("userId", e.target.value)}
        />
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
        <Button variant="outline" onClick={() => setParams({ page: 1, pageSize: 20 })}>
          重置
        </Button>
      </div>

      {/* 列表 */}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-40">时间</TableHead>
            <TableHead>用户</TableHead>
            <TableHead>模块</TableHead>
            <TableHead>操作类型</TableHead>
            <TableHead>描述</TableHead>
            <TableHead>业务编号</TableHead>
            <TableHead>耗时(ms)</TableHead>
            <TableHead>结果</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && (
            <TableRow>
              <TableCell colSpan={8} className="text-center text-muted-foreground">
                加载中...
              </TableCell>
            </TableRow>
          )}
          {data?.list.map((log) => (
            <TableRow key={log.id}>
              <TableCell className="text-muted-foreground text-sm">
                {new Date(log.createTime).toLocaleString("zh-CN")}
              </TableCell>
              <TableCell>{log.username || log.userId}</TableCell>
              <TableCell>{log.module}</TableCell>
              <TableCell>{TYPE_LABEL[log.type] ?? log.type}</TableCell>
              <TableCell className="max-w-48 truncate" title={log.description}>
                {log.description}
              </TableCell>
              <TableCell className="max-w-32 truncate text-muted-foreground text-sm">
                {log.bizNo}
              </TableCell>
              <TableCell>{log.durationMs}</TableCell>
              <TableCell>
                {log.success ? (
                  <Badge variant="default" className="bg-emerald-500">
                    成功
                  </Badge>
                ) : (
                  <Badge variant="destructive" title={log.errorMessage}>
                    失败
                  </Badge>
                )}
              </TableCell>
            </TableRow>
          ))}
          {!isLoading && data?.list.length === 0 && (
            <TableRow>
              <TableCell colSpan={8} className="text-center text-muted-foreground">
                暂无操作日志
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      {/* 分页 */}
      <TablePagination
        page={params.page ?? 1}
        pageSize={params.pageSize ?? 20}
        total={data?.total ?? 0}
        onChangePage={(page) => setParams((p) => ({ ...p, page }))}
        onChangePageSize={(pageSize) => setParams((p) => ({ ...p, pageSize, page: 1 }))}
      />
    </PageContainer>
  )
}
