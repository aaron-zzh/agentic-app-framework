/**
 * /studio/templates——已发布 AIGC Project Blueprint 目录。
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { LottieIcon } from "@/components/animate"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { getProjectTypeConfig } from "@/features/studio/content"
import type { AigcProjectBlueprint } from "@/lib/api/rest/ai/aigc"
import { useAigcProjectBlueprints } from "@/lib/api/rest/ai/aigc"

function BlueprintCard({ blueprint }: { blueprint: AigcProjectBlueprint }) {
  const type = getProjectTypeConfig({ code: blueprint.projectTypeCode, name: "" })
  const Icon = type.icon
  return (
    <GlassCard glow={type.tone} className="overflow-hidden">
      <div className="flex h-32 items-center justify-center bg-foreground/[0.04] text-muted-foreground">
        <Icon className="size-10" />
      </div>
      <div className="flex flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h2 className="font-medium text-sm">{blueprint.name}</h2>
            <p className="text-muted-foreground text-xs">v{blueprint.blueprintVersion}</p>
          </div>
          <NeonChip tone={type.tone} size="sm">
            {type.label}
          </NeonChip>
        </div>
        <p className="line-clamp-2 min-h-10 text-muted-foreground text-xs leading-5">
          {blueprint.description || "已发布项目蓝图"}
        </p>
        <div className="flex items-center justify-between gap-2">
          <span className="text-muted-foreground text-xs">{blueprint.productionMode}</span>
          <GlowButton
            nativeButton={false}
            render={<Link href="/studio/projects/new" />}
            tone="primary"
            size="sm"
          >
            创建项目
          </GlowButton>
        </div>
      </div>
    </GlassCard>
  )
}

export default function StudioTemplatesPage() {
  const { data, isLoading } = useAigcProjectBlueprints({ status: "published", pageSize: 100 })
  const blueprints = data?.list ?? []

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 p-6">
      <header>
        <h1 className="font-semibold text-xl">项目蓝图</h1>
        <p className="text-muted-foreground text-sm">从已发布蓝图物化唯一 AigcProject。</p>
      </header>
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={`blueprint-${index}`} className="h-64 rounded-2xl" />
          ))}
        </div>
      ) : blueprints.length === 0 ? (
        <GlassCard glow="none">
          <Empty className="min-h-72">
            <EmptyHeader>
              <LottieIcon name="cat" width={120} height={120} loop />
              <EmptyTitle>暂无已发布蓝图</EmptyTitle>
              <EmptyDescription>项目创建必须绑定已发布 AIGC Project Blueprint。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        </GlassCard>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {blueprints.map((blueprint) => (
            <BlueprintCard key={blueprint.id} blueprint={blueprint} />
          ))}
        </div>
      )}
    </div>
  )
}
