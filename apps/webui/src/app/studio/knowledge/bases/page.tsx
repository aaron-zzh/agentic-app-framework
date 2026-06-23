/**
 * /studio/knowledge/bases——知识库（迁移自 workspace/knowledge，不 redirect）
 * 复用知识库业务组件，外套 Studio 风格层
 * @author AaronZZH & Kiro
 */

"use client"

import { BookOpen, Plus, Search } from "lucide-react"
import Link from "next/link"
import { useId, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton, SectionHaze } from "@/components/studio"
import { Button } from "@/components/ui/button"
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
import { useCreateKnowledgeBase, useKnowledgeBases } from "@/lib/queries/use-knowledge"

export default function StudioKnowledgeBasesPage() {
  const [search, setSearch] = useState("")
  const [open, setOpen] = useState(false)
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const nameId = useId()
  const descId = useId()

  const { data, isLoading } = useKnowledgeBases({ search })
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
    <div className="relative mx-auto max-w-5xl p-6">
      <SectionHaze variant="cyan" />
      <div className="relative space-y-6">
        {/* 标题栏 */}
        <div className="flex items-center justify-between gap-3">
          <h1 className="font-semibold text-xl">知识库</h1>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger
              render={
                <GlowButton tone="violet">
                  <Plus className="size-4" />
                  新建知识库
                </GlowButton>
              }
            />
            <DialogContent>
              <DialogHeader>
                <DialogTitle>新建知识库</DialogTitle>
              </DialogHeader>
              <div className="space-y-4 pt-2">
                <div className="space-y-1.5">
                  <label htmlFor={nameId} className="font-medium text-sm">
                    名称
                  </label>
                  <Input
                    id={nameId}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="知识库名称"
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor={descId} className="font-medium text-sm">
                    描述（可选）
                  </label>
                  <Input
                    id={descId}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="简要描述"
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="outline" onClick={() => setOpen(false)}>
                    取消
                  </Button>
                  <GlowButton
                    tone="violet"
                    onClick={handleCreate}
                    disabled={isPending || !name.trim()}
                  >
                    {isPending ? "创建中..." : "创建"}
                  </GlowButton>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>

        {/* 搜索 */}
        <div className="relative">
          <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索知识库..."
            className="bg-muted/20 pl-9"
          />
        </div>

        {/* 知识库卡片列表 */}
        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={`kb-sk-${i}`} className="h-32 rounded-2xl" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <Empty>
            <EmptyHeader>
              <BookOpen className="size-8" />
            </EmptyHeader>
            <EmptyTitle>暂无知识库</EmptyTitle>
            <EmptyDescription>创建第一个知识库，开始管理你的知识</EmptyDescription>
          </Empty>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((kb) => (
              <Link key={kb.id} href={`/knowledge/${kb.id}`}>
                <GlassCard interactive glow="cyan" className="h-full">
                  <GlassCardBody className="space-y-2">
                    <div className="flex items-start gap-3">
                      <div className="flex size-10 items-center justify-center rounded-xl bg-cyan-500/10">
                        <BookOpen className="size-5 text-cyan-400" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium">{kb.name}</p>
                        {kb.description && (
                          <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">
                            {kb.description}
                          </p>
                        )}
                      </div>
                    </div>
                    {(kb as unknown as { docCount?: number }).docCount !== undefined && (
                      <p className="text-muted-foreground text-xs">
                        {(kb as unknown as { docCount: number }).docCount} 篇文档
                      </p>
                    )}
                  </GlassCardBody>
                </GlassCard>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
