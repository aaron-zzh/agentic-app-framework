/**
 * 数字人列表页
 * @author Kiro
 */

"use client"

import { Plus } from "lucide-react"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useDigitalHumans, useDeleteDigitalHuman } from "@/lib/api/rest/digital-human"
import { DigitalHumanCard } from "../components/DigitalHumanCard"
import type { DigitalHumanListParams } from "../types"

export default function DigitalHumanList() {
  const router = useRouter()
  const [params, setParams] = useState<DigitalHumanListParams>({
    page: 0,
    size: 20,
    status: "active"
  })

  const { data, isLoading } = useDigitalHumans(params)
  const deleteMutation = useDeleteDigitalHuman()

  const handleCreate = () => {
    router.push("/aigc/projects/create?blueprint=digital-human-asset-generation")
  }

  const handleDelete = async (id: string) => {
    if (!confirm("确认删除此数字人？删除后无法恢复。")) return
    await deleteMutation.mutateAsync(id)
  }

  return (
    <div className="container py-6">
      {/* 标题栏 */}
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-semibold text-2xl">数字人管理</h1>
        <Button onClick={handleCreate}>
          <Plus className="mr-2 size-4" />
          创建数字人
        </Button>
      </div>

      {/* 筛选栏 */}
      <div className="mb-6 flex gap-4">
        <Input
          placeholder="搜索数字人名称..."
          value={params.name || ""}
          onChange={(e) =>
            setParams((prev) => ({ ...prev, name: e.target.value || undefined, page: 0 }))
          }
          className="max-w-xs"
        />
      </div>

      {/* 卡片网格 */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-[3/4] animate-pulse rounded-lg bg-muted" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="py-12 text-center">
          <p className="mb-4 text-muted-foreground">暂无数字人</p>
          <Button onClick={handleCreate}>创建第一个数字人</Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {data.content.map((dh) => (
            <DigitalHumanCard
              key={dh.id}
              digitalHuman={dh}
              onView={(id) => router.push(`/digital-humans/${id}`)}
              onEdit={(id) => router.push(`/digital-humans/${id}`)}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}

      {/* 分页 */}
      {data && data.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={params.page === 0}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) - 1 }))}
          >
            上一页
          </Button>
          <span className="text-muted-foreground text-sm">
            第 {(params.page || 0) + 1} / {data.totalPages} 页
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={(params.page || 0) >= data.totalPages - 1}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) + 1 }))}
          >
            下一页
          </Button>
        </div>
      )}
    </div>
  )
}
