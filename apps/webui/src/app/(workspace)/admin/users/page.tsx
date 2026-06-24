/**
 * 用户管理列表页
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Upload } from "lucide-react"
import { useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { TablePagination } from "@/components/table/TablePagination"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH1 } from "@/components/ui/typography"
import { adminUserApi, type UserListParams } from "@/lib/api/rest/admin/user"
import { useAdminUserList } from "@/lib/queries/use-admin-user"

export default function AdminUserPage() {
  const [params, setParams] = useState<UserListParams>({ page: 1, pageSize: 20 })
  const { data, isLoading } = useAdminUserList(params)
  const queryClient = useQueryClient()

  // 导入弹窗状态
  const [importOpen, setImportOpen] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importFile, setImportFile] = useState<File | null>(null)
  const [importResult, setImportResult] = useState<{
    successCount: number
    failureCount: number
    failureMessages: string[]
  } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  function updateFilter(key: keyof UserListParams, value: string) {
    setParams((prev) => ({ ...prev, [key]: value || undefined, page: 1 }))
  }

  function openImport() {
    setImportFile(null)
    setImportResult(null)
    setImportOpen(true)
  }

  async function handleImport() {
    if (!importFile) return
    setImporting(true)
    try {
      const result = await adminUserApi.import(importFile)
      setImportResult(result)
      // 刷新列表
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] })
    } catch (_e) {
      setImportResult({
        successCount: 0,
        failureCount: 1,
        failureMessages: ["导入失败，请检查文件格式"]
      })
    } finally {
      setImporting(false)
    }
  }

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1>用户管理</TypographyH1>
        <Button onClick={openImport} variant="outline" size="sm">
          <Upload className="size-4" />
          导入用户
        </Button>
      </div>

      {/* 筛选栏 */}
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          placeholder="用户名"
          className="w-40"
          onChange={(e) => updateFilter("username", e.target.value)}
        />
        <Input
          placeholder="昵称"
          className="w-40"
          onChange={(e) => updateFilter("nickname", e.target.value)}
        />
      </div>

      {/* 表格 */}
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>用户名</TableHead>
              <TableHead>昵称</TableHead>
              <TableHead>手机号</TableHead>
              <TableHead>邮箱</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>创建时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                  加载中...
                </TableCell>
              </TableRow>
            ) : data?.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                  暂无数据
                </TableCell>
              </TableRow>
            ) : (
              data?.list.map((user) => (
                <TableRow key={user.id}>
                  <TableCell className="text-muted-foreground text-sm">{user.id}</TableCell>
                  <TableCell>{user.username}</TableCell>
                  <TableCell>{user.nickname ?? "-"}</TableCell>
                  <TableCell>{user.phone ?? "-"}</TableCell>
                  <TableCell>{user.email ?? "-"}</TableCell>
                  <TableCell>
                    <Badge variant={user.status === 0 ? "default" : "secondary"}>
                      {user.status === 0 ? "正常" : "禁用"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {user.createTime ? user.createTime.replace("T", " ").slice(0, 16) : "-"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* 分页 */}
      {data && (
        <TablePagination
          page={params.page ?? 1}
          pageSize={params.pageSize ?? 20}
          total={data.total}
          onChangePage={(page) => setParams((prev) => ({ ...prev, page }))}
          onChangePageSize={(pageSize) => setParams((prev) => ({ ...prev, pageSize, page: 1 }))}
        />
      )}

      {/* 导入弹窗 */}
      <Dialog open={importOpen} onOpenChange={setImportOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>批量导入用户</DialogTitle>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="text-muted-foreground text-sm">
              上传 Excel 文件，表头包含「手机号」列。系统自动以手机号为账号，密码为{" "}
              <code className="rounded bg-muted px-1 text-xs">web4.0+手机后四位</code>。
              重复账号将提示导入失败。{" "}
              <button
                type="button"
                className="text-primary underline-offset-2 hover:underline"
                onClick={() => adminUserApi.downloadTemplate()}
              >
                下载导入模板
              </button>
            </div>

            {/* 文件选择 */}
            <button
              type="button"
              className="flex w-full cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed p-6 transition-colors hover:border-primary/50"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload className="size-8 text-muted-foreground" />
              <span className="text-muted-foreground text-sm">
                {importFile ? importFile.name : "点击选择 .xlsx / .xls 文件"}
              </span>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                className="hidden"
                onChange={(e) => {
                  setImportFile(e.target.files?.[0] ?? null)
                  setImportResult(null)
                }}
              />
            </button>

            {/* 导入结果 */}
            {importResult && (
              <div className="rounded-lg border p-3 text-sm">
                <p className="font-medium">
                  成功 {importResult.successCount} 条，失败 {importResult.failureCount} 条
                </p>
                {importResult.failureMessages.length > 0 && (
                  <ul className="mt-2 max-h-40 space-y-1 overflow-y-auto text-destructive text-xs">
                    {importResult.failureMessages.map((msg, i) => (
                      <li key={i}>{msg}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setImportOpen(false)}>
              关闭
            </Button>
            <Button onClick={handleImport} disabled={!importFile || importing}>
              {importing ? "导入中..." : "开始导入"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  )
}
