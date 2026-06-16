/**
 * 页面管理——PageDef 列表 + 编辑器入口
 * @author AaronZZH & Kiro
 */

"use client"

import { Eye, Pencil, Plus } from "lucide-react"
import Link from "next/link"
import { useCallback, useState } from "react"
import { toast } from "sonner"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent } from "@/components/ui/dialog"
import { TypographyH1 } from "@/components/ui/typography"
import { PageEditorView } from "@/features/page-editor"
// 导入以触发 Section 组件注册
import "@/features/page-engine/sections"
import { aafLandingPageDef } from "@/features/page-engine/presets/aaf-landing"
import type { PageDef } from "@/features/page-engine/types"

/** 模拟页面列表（v0.1 使用本地数据，后续接 API） */
const MOCK_PAGES: Array<{
  id: string
  title: string
  slug: string
  status: "draft" | "published"
  updatedAt: string
}> = [
  { id: "1", title: "AAF 产品首页", slug: "home", status: "published", updatedAt: "2026-05-19" },
  { id: "2", title: "定价页", slug: "pricing", status: "draft", updatedAt: "2026-05-18" }
]

export default function PagesAdminPage() {
  const [editingPage, setEditingPage] = useState<PageDef | null>(null)

  /** 打开编辑器 */
  const handleEdit = useCallback((slug: string) => {
    // v0.1 仅支持编辑 aaf-landing 预设
    if (slug === "home") {
      setEditingPage(aafLandingPageDef)
    } else {
      toast.info("该页面暂无配置数据")
    }
  }, [])

  /** 保存 */
  const handleSave = useCallback((page: PageDef) => {
    toast.success(`页面「${page.title}」已保存`)
    setEditingPage(null)
  }, [])

  /** 发布 */
  const handlePublish = useCallback((page: PageDef) => {
    toast.success(`页面「${page.title}」已发布`)
    setEditingPage(null)
  }, [])

  return (
    <PageContainer>
      {/* 页面列表 */}
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1>页面管理</TypographyH1>
        <Button size="sm">
          <Plus className="mr-1 size-4" />
          新建页面
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {MOCK_PAGES.map((page) => (
          <Card key={page.id}>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base">{page.title}</CardTitle>
                <Badge variant={page.status === "published" ? "default" : "secondary"}>
                  {page.status === "published" ? "已发布" : "草稿"}
                </Badge>
              </div>
              <CardDescription>/{page.slug}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground text-xs">更新于 {page.updatedAt}</span>
                <div className="flex gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleEdit(page.slug)}
                    aria-label="编辑"
                  >
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label="预览"
                    nativeButton={false}
                    render={
                      <Link
                        href={`/${page.slug === "home" ? "" : page.slug}`}
                        target="_blank"
                        rel="noreferrer"
                      />
                    }
                  >
                    <Eye className="size-4" />
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 编辑器全屏对话框 */}
      <Dialog open={!!editingPage} onOpenChange={(open) => !open && setEditingPage(null)}>
        <DialogContent className="h-[90vh] max-w-[95vw] p-0">
          {editingPage && (
            <PageEditorView
              initialPage={editingPage}
              onSave={handleSave}
              onPublish={handlePublish}
            />
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  )
}
