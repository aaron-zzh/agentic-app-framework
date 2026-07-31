/**
 * 知识库列表页——卡片展示 + 搜索 + 创建
 * @author AaronZZH & Kiro
 */

"use client"

import { BookOpen, Plus } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { TypographyH1 } from "@/components/ui/typography"
import { useCreateKnowledgeBase, useKnowledgeBases } from "@/lib/api/rest/knowledge/knowledge"

export default function KnowledgeListPage() {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")

  const { data, isLoading } = useKnowledgeBases()
  const { mutate: create, isPending } = useCreateKnowledgeBase()

  const items = data?.list ?? []

  function handleCreate() {
    if (!name.trim()) return
    create(
      { name: name.trim(), description: description.trim() || undefined },
      {
        onSuccess: () => {
          setOpen(false)
          setName("")
          setDescription("")
        }
      }
    )
  }

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1 className="text-2xl">知识库</TypographyH1>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger
            render={
              <Button>
                <Plus className="mr-1 size-4" />
                创建知识库
              </Button>
            }
          />
          <DialogContent>
            <DialogHeader>
              <DialogTitle>创建知识库</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <Input
                placeholder="知识库名称"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <Input
                placeholder="描述（可选）"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <Button
                className="w-full"
                onClick={handleCreate}
                disabled={isPending || !name.trim()}
              >
                {isPending ? "创建中..." : "创建"}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {/* 列表 */}
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={`sk-${i}`} className="h-32 rounded-lg" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>暂无知识库</EmptyTitle>
            <EmptyDescription>点击右上角按钮创建第一个知识库</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((kb) => (
            <Link key={kb.id} href={`/knowledge/${kb.id}`}>
              <Card className="transition-colors hover:border-primary/50">
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <BookOpen className="size-5 text-primary" />
                    <CardTitle className="text-base">{kb.name}</CardTitle>
                  </div>
                  <CardDescription className="line-clamp-2">
                    {kb.description || "暂无描述"}
                  </CardDescription>
                  <div className="mt-2 flex items-center gap-4 text-muted-foreground text-xs">
                    <span>{kb.documentCount} 篇文档</span>
                    <span>更新于 {new Date(kb.updateTime).toLocaleDateString()}</span>
                  </div>
                </CardHeader>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </PageContainer>
  )
}
