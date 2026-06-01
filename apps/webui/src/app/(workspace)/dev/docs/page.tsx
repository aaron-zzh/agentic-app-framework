/**
 * 开发文档页面（/workspace/dev/docs）
 * 三栏 ResizablePanel：左侧文件树+大纲 | 中间文档/图谱 | 右侧 Kiro Agent 对话
 * @author AaronZZH & Kiro
 */
"use client"

import { DndContext } from "@dnd-kit/core"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Edit, FileText, Plus, RefreshCw } from "lucide-react"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import { DocOutline } from "@/components/docs/DocOutline"
import { DocRelationGraphView } from "@/components/docs/DocRelationGraphView"
import { DocTree } from "@/components/docs/DocTree"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Chatter } from "@/features/chatter"
import { RichTextEditor } from "@/features/rich-text-editor"
import { request } from "@/lib/api/rest/entity/crud"
import { useDocEvents } from "@/lib/hooks/use-doc-events"
import {
  autodevDocKeys,
  useAutodevDoc,
  useAutodevDocRelations,
  useAutodevDocTree,
  useImportAutodevDocs,
  useUpdateAutodevDoc
} from "@/lib/queries/use-autodev-documents"
import { AutodevDocCreateDialog } from "./AutodevDocCreateDialog"

/** 获取可用 agent 角色列表 */
function useKiroAgents() {
  return useQuery({
    queryKey: ["kiro", "agents"],
    queryFn: () => request<string[]>("/autodev/kiro/agents")
  })
}

export default function DevDocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [tab, setTab] = useState("content")
  const [editOpen, setEditOpen] = useState(false)
  const [editContent, setEditContent] = useState("")
  const [createOpen, setCreateOpen] = useState(false)
  const [agentRole, setAgentRole] = useState("")

  const qc = useQueryClient()
  const { data: tree, isLoading: treeLoading } = useAutodevDocTree()
  const { data: doc, isLoading: docLoading } = useAutodevDoc(selectedId)
  const { data: relations, isLoading: relationsLoading } = useAutodevDocRelations(
    tab === "graph" ? selectedId : null
  )
  const { mutate: update, isPending: saving } = useUpdateAutodevDoc()
  const { mutate: importDocs, isPending: importing } = useImportAutodevDocs()
  const { data: agents } = useKiroAgents()

  // SSE 订阅文档变更，自动刷新
  const onDocUpdate = useCallback(() => {
    if (selectedId) qc.invalidateQueries({ queryKey: autodevDocKeys.detail(selectedId) })
  }, [selectedId, qc])
  useDocEvents(selectedId, onDocUpdate)

  function handleEdit() {
    if (!doc) return
    setEditContent(doc.content ?? "")
    setEditOpen(true)
  }

  function handleSave() {
    if (!selectedId) return
    update(
      { id: selectedId, content: editContent },
      {
        onSuccess: () => {
          setEditOpen(false)
          toast.success("文档已保存")
        },
        onError: () => toast.error("保存失败")
      }
    )
  }

  return (
    <PageContainer disablePadding>
      <DndContext>
        <ResizablePanelGroup direction="horizontal" className="h-[calc(100vh-8rem)]">
          {/* 左侧：文件树 + 大纲 */}
          <ResizablePanel defaultSize={18} minSize={12} maxSize={30}>
            <div className="flex h-full flex-col overflow-hidden border-r">
              <div className="flex items-center justify-between border-b px-3 py-2">
                <span className="font-medium text-sm">开发文档</span>
                <div className="flex gap-1">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => importDocs()}
                    disabled={importing}
                    title="同步本地文档"
                  >
                    <RefreshCw className={`size-3.5 ${importing ? "animate-spin" : ""}`} />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setCreateOpen(true)}
                    title="新建文档"
                  >
                    <Plus className="size-3.5" />
                  </Button>
                </div>
              </div>
              <div className="min-h-0 flex-1 overflow-y-auto p-2">
                {treeLoading ? (
                  <div className="space-y-2">
                    {Array.from({ length: 8 }).map((_, i) => (
                      <Skeleton key={i} className="h-5 w-full" />
                    ))}
                  </div>
                ) : (
                  <DocTree
                    nodes={tree ?? []}
                    selectedId={selectedId}
                    onSelect={(id) => {
                      setSelectedId(id)
                      setTab("content")
                    }}
                    draggable
                  />
                )}
                {/* 文档内标题大纲 */}
                <DocOutline content={doc?.content} />
              </div>
            </div>
          </ResizablePanel>

          <ResizableHandle withHandle />

          {/* 中间：文档内容 / 关系图谱 */}
          <ResizablePanel defaultSize={50} minSize={30}>
            <div className="flex h-full flex-col">
              {!selectedId ? (
                <div className="flex h-full items-center justify-center text-muted-foreground">
                  <div className="text-center">
                    <FileText className="mx-auto mb-2 size-12 opacity-30" />
                    <p className="text-sm">选择左侧文档查看内容</p>
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between border-b px-4 py-2">
                    <h2 className="truncate font-semibold text-sm">{doc?.title ?? "加载中..."}</h2>
                    <Button size="sm" variant="outline" onClick={handleEdit} disabled={!doc}>
                      <Edit className="mr-1 size-3.5" />
                      编辑
                    </Button>
                  </div>
                  <Tabs
                    value={tab}
                    onValueChange={setTab}
                    className="flex flex-1 flex-col overflow-hidden"
                  >
                    <TabsList className="mx-4 mt-2 self-start">
                      <TabsTrigger value="content">内容</TabsTrigger>
                      <TabsTrigger value="graph">关系图谱</TabsTrigger>
                    </TabsList>
                    <TabsContent value="content" className="flex-1 overflow-y-auto p-4">
                      {docLoading ? (
                        <div className="space-y-3">
                          {Array.from({ length: 10 }).map((_, i) => (
                            <Skeleton key={i} className="h-4 w-full" />
                          ))}
                        </div>
                      ) : (
                        <pre className="whitespace-pre-wrap font-mono text-sm leading-relaxed">
                          {doc?.content}
                        </pre>
                      )}
                    </TabsContent>
                    <TabsContent value="graph" className="flex-1 overflow-hidden">
                      <DocRelationGraphView
                        data={relations}
                        isLoading={relationsLoading}
                        onSelectDoc={setSelectedId}
                      />
                    </TabsContent>
                  </Tabs>
                </>
              )}
            </div>
          </ResizablePanel>

          <ResizableHandle withHandle />

          {/* 右侧：Kiro Agent 对话（内嵌，无遮罩） */}
          <ResizablePanel defaultSize={32} minSize={20} maxSize={50}>
            <Chatter
              preset="kiro"
              layout="panel"
              agentRole={agentRole}
              toolbar={
                <Select value={agentRole} onValueChange={(v) => setAgentRole(v ?? "")}>
                  <SelectTrigger className="h-7 text-xs">
                    <SelectValue placeholder="选择 Agent..." />
                  </SelectTrigger>
                  <SelectContent>
                    {(
                      agents ?? [
                        "kiro_default",
                        "product",
                        "architect",
                        "developer-service",
                        "tester",
                        "qa"
                      ]
                    ).map((role) => (
                      <SelectItem key={role} value={role} className="text-xs">
                        {role}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              }
            />
          </ResizablePanel>
        </ResizablePanelGroup>
      </DndContext>

      {/* 编辑弹窗（非模式，不虚化背景） */}
      <Dialog open={editOpen} onOpenChange={setEditOpen} modal={false}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑：{doc?.title}</DialogTitle>
          </DialogHeader>
          <RichTextEditor
            value={editContent}
            onChange={setEditContent}
            preset="document"
            mode="markdown"
            minHeight={400}
          />
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setEditOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <AutodevDocCreateDialog open={createOpen} onOpenChange={setCreateOpen} />
    </PageContainer>
  )
}
