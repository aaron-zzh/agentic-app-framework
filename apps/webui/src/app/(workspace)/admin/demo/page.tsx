/**
 * 演示模式管理页（Super Admin 专属）
 * 一键加载 / 清理演示数据
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Database, Trash2 } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { TypographyH1 } from "@/components/ui/typography"
import { demoApi } from "@/lib/api/rest/system/demo"

export default function DemoPage() {
  const [cleanOpen, setCleanOpen] = useState(false)

  const { mutate: load, isPending: isLoading } = useMutation({
    mutationFn: () => demoApi.load(),
    onSuccess: () => toast.success("演示数据加载成功"),
    onError: () => toast.error("演示数据加载失败")
  })

  const { mutate: clean, isPending: isCleaning } = useMutation({
    mutationFn: () => demoApi.clean(),
    onSuccess: () => {
      toast.success("演示数据清理成功")
      setCleanOpen(false)
    },
    onError: () => toast.error("演示数据清理失败")
  })

  return (
    <PageContainer>
      <TypographyH1 className="mb-6">演示模式</TypographyH1>

      <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
        {/* 加载演示数据 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Database className="size-4" />
              加载演示数据
            </CardTitle>
            <CardDescription>
              向系统注入预设的演示数据，操作幂等，重复执行不会产生重复数据。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => load()} disabled={isLoading}>
              {isLoading ? "加载中..." : "加载演示数据"}
            </Button>
          </CardContent>
        </Card>

        {/* 清理演示数据 */}
        <Card className="border-destructive/40">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Trash2 className="size-4 text-destructive" />
              清理演示数据
            </CardTitle>
            <CardDescription>
              删除所有演示数据（演示用户、通知等关联记录）。此操作不可恢复。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="destructive" disabled={isCleaning} onClick={() => setCleanOpen(true)}>
              {isCleaning ? "清理中..." : "清理演示数据"}
            </Button>
          </CardContent>
        </Card>
      </div>

      {/* 清理确认弹窗 */}
      <AlertDialog open={cleanOpen} onOpenChange={setCleanOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认清理演示数据？</AlertDialogTitle>
            <AlertDialogDescription>
              此操作将删除所有演示数据及其关联记录，且不可恢复。请确认后继续。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => clean()}
            >
              确认清理
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </PageContainer>
  )
}
