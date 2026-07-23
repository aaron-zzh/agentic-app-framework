/**
 * 实体定义管理页面——无代码编辑器 v0.1（Monaco JSON 编辑）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { toast } from "sonner"

import { PageContainer } from "@/components/common/PageContainer"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { TypographyH1 } from "@/components/ui/typography"
import { EntityDefEditor } from "@/features/entity-editor/EntityDefEditor"
import { EntityDefList } from "@/features/entity-editor/EntityDefList"
import type { EntityDefRecord } from "@/lib/api/rest/entity/entity-def"
import { useEntityDefs, useUpdateEntityDef } from "@/lib/queries/use-entity-defs"

export default function EntitiesPage() {
  const { data: entities, isLoading } = useEntityDefs()
  const updateMutation = useUpdateEntityDef()

  const [selected, setSelected] = useState<EntityDefRecord | null>(null)

  /** 选中实体 */
  const handleSelect = useCallback((item: EntityDefRecord) => {
    setSelected(item)
  }, [])

  /** 保存 */
  const handleSave = useCallback(
    (json: string) => {
      try {
        const config = JSON.parse(json) as Record<string, unknown>

        if (selected) {
          updateMutation.mutate(
            { id: selected.id, data: { config } },
            {
              onSuccess: () => toast.success("保存成功"),
              onError: () => {}
            }
          )
        }
      } catch {
        toast.error("JSON 格式错误，无法保存")
      }
    },
    [selected, updateMutation]
  )

  /** 当前编辑器的 JSON 值 */
  const editorValue = selected ? JSON.stringify(selected.config, null, 2) : ""

  return (
    <PageContainer className="h-[calc(100vh-var(--layout-header-height)-2rem)]">
      <TypographyH1 className="mb-4">实体定义管理</TypographyH1>

      <div
        className="flex-1 overflow-hidden rounded-md border"
        style={{ height: "calc(100% - 4rem)" }}
      >
        <ResizablePanelGroup orientation="horizontal">
          {/* 左侧列表 */}
          <ResizablePanel defaultSize="20%" minSize="15%" maxSize="35%">
            <EntityDefList
              items={entities ?? []}
              selectedId={selected?.id}
              onSelect={handleSelect}
            />
            {isLoading && (
              <p className="p-3 text-center text-muted-foreground text-sm">加载中...</p>
            )}
          </ResizablePanel>
          <ResizableHandle />
          {/* 右侧编辑器 + 预览 */}
          <ResizablePanel defaultSize="80%">
            {selected ? (
              <EntityDefEditor
                value={editorValue}
                runtimeSlug={selected.slug}
                runtimeApiPath={selected.apiPath}
                builtin={selected.builtin}
                onSave={handleSave}
                saving={updateMutation.isPending}
              />
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground">
                选择一个已注册的实体定义
              </div>
            )}
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>
    </PageContainer>
  )
}
