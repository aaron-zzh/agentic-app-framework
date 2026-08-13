/**
 * 知识库后台运维列表。
 * @author AaronZZH & Kiro
 */

"use client"

import { ExternalLink, RotateCcw } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { TablePagination } from "@/components/table/TablePagination"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
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
import {
  type KnowledgeBaseMaintenance,
  type KnowledgeMaintenanceListParams,
  useAdminKnowledgeList
} from "@/lib/api/rest/admin"
import type { KnowledgeBaseVisibility } from "@/lib/types/knowledge"

const VISIBILITY_LABEL: Record<KnowledgeBaseVisibility, string> = {
  PRIVATE: "私有",
  ORG: "组织",
  SYSTEM_PUBLIC: "系统公开"
}

const INITIAL_PARAMS: KnowledgeMaintenanceListParams = {
  pageNo: 1,
  pageSize: 20,
  sort: "createTime:desc"
}

export default function AdminKnowledgePage() {
  const [params, setParams] = useState<KnowledgeMaintenanceListParams>(INITIAL_PARAMS)
  const { data, isLoading } = useAdminKnowledgeList(params)

  function updateFilters(patch: Partial<KnowledgeMaintenanceListParams>) {
    setParams((current) => ({ ...current, ...patch, pageNo: 1 }))
  }

  function optionalId(value: string): number | undefined {
    if (value.trim() === "") return undefined
    const parsed = Number(value)
    return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
  }

  return (
    <PageContainer>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <TypographyH1>知识库运维</TypographyH1>
          <p className="mt-1 text-muted-foreground text-sm">
            跨个人范围查看当前组织知识库，执行失败重试与投影重建。
          </p>
        </div>
        <Button variant="outline" onClick={() => setParams(INITIAL_PARAMS)}>
          <RotateCcw data-icon="inline-start" />
          重置筛选
        </Button>
      </div>

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <Input
          aria-label="知识库名称"
          placeholder="知识库名称"
          value={params.name ?? ""}
          onChange={(event) => updateFilters({ name: event.target.value || undefined })}
        />
        <Input
          aria-label="归属用户 ID"
          inputMode="numeric"
          placeholder="Owner ID"
          value={params.ownerId ?? ""}
          onChange={(event) => updateFilters({ ownerId: optionalId(event.target.value) })}
        />
        <Input
          aria-label="组织 ID"
          inputMode="numeric"
          placeholder="Org ID"
          value={params.orgId ?? ""}
          onChange={(event) => updateFilters({ orgId: optionalId(event.target.value) })}
        />
        <Input
          aria-label="工作空间 ID"
          inputMode="numeric"
          placeholder="Workspace ID"
          value={params.workspaceId ?? ""}
          onChange={(event) => updateFilters({ workspaceId: optionalId(event.target.value) })}
        />
        <Select
          value={params.visibility ?? "all"}
          onValueChange={(value) =>
            updateFilters({
              visibility: value === "all" ? undefined : (value as KnowledgeBaseVisibility)
            })
          }
        >
          <SelectTrigger aria-label="可见范围">
            <SelectValue placeholder="可见范围" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="all">全部范围</SelectItem>
              <SelectItem value="PRIVATE">私有</SelectItem>
              <SelectItem value="ORG">组织</SelectItem>
              <SelectItem value="SYSTEM_PUBLIC">系统公开</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
        <Select
          value={params.status === undefined ? "all" : String(params.status)}
          onValueChange={(value) =>
            updateFilters({ status: value === "all" ? undefined : value === "0" ? 0 : 1 })
          }
        >
          <SelectTrigger aria-label="知识库状态">
            <SelectValue placeholder="状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="all">全部状态</SelectItem>
              <SelectItem value="0">启用</SelectItem>
              <SelectItem value="1">禁用</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>知识库</TableHead>
              <TableHead>范围</TableHead>
              <TableHead>Owner</TableHead>
              <TableHead>组织 / 工作空间</TableHead>
              <TableHead>文档</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>更新时间</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} className="py-10 text-center text-muted-foreground">
                  加载中...
                </TableCell>
              </TableRow>
            ) : data?.list.length ? (
              data.list.map((knowledgeBase) => (
                <KnowledgeMaintenanceRow key={knowledgeBase.id} knowledgeBase={knowledgeBase} />
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={9} className="py-10 text-center text-muted-foreground">
                  当前组织范围内暂无知识库
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {data ? (
        <TablePagination
          page={params.pageNo ?? 1}
          pageSize={params.pageSize ?? 20}
          total={data.total}
          onChangePage={(pageNo) => setParams((current) => ({ ...current, pageNo }))}
          onChangePageSize={(pageSize) =>
            setParams((current) => ({ ...current, pageSize, pageNo: 1 }))
          }
        />
      ) : null}
    </PageContainer>
  )
}

function KnowledgeMaintenanceRow({ knowledgeBase }: { knowledgeBase: KnowledgeBaseMaintenance }) {
  return (
    <TableRow>
      <TableCell className="text-muted-foreground text-sm">{knowledgeBase.id}</TableCell>
      <TableCell>
        <p className="font-medium">{knowledgeBase.name}</p>
        <p className="max-w-64 truncate text-muted-foreground text-xs">{knowledgeBase.stableId}</p>
      </TableCell>
      <TableCell>
        <Badge variant="outline">{VISIBILITY_LABEL[knowledgeBase.visibility]}</Badge>
      </TableCell>
      <TableCell>{knowledgeBase.ownerId ?? "-"}</TableCell>
      <TableCell className="text-sm">
        {knowledgeBase.orgId ?? "-"} / {knowledgeBase.workspaceId ?? "-"}
      </TableCell>
      <TableCell>{knowledgeBase.documentCount}</TableCell>
      <TableCell>
        <Badge variant={knowledgeBase.status === 0 ? "default" : "secondary"}>
          {knowledgeBase.status === 0 ? "启用" : "禁用"}
        </Badge>
      </TableCell>
      <TableCell className="text-muted-foreground text-sm">
        {new Date(knowledgeBase.updateTime).toLocaleString("zh-CN")}
      </TableCell>
      <TableCell className="text-right">
        <Button
          nativeButton={false}
          variant="outline"
          size="sm"
          render={<Link href={`/admin/knowledge/${knowledgeBase.id}`} />}
        >
          <ExternalLink data-icon="inline-start" />
          运维详情
        </Button>
      </TableCell>
    </TableRow>
  )
}
