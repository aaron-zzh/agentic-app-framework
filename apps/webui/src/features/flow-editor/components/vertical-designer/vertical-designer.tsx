/**
 * 竖向审批设计器——钉钉/飞书风格卡片式布局
 * @author Kiro
 *
 * TODO: 文件 450+ 行，待拆分 NodeCard / ConditionBranches / AddButton 为独立文件
 *
 * @example
 * ```tsx
 * import { VerticalDesigner } from "@/features/flow-editor"
 *
 * <VerticalDesigner
 *   value={flowData}
 *   onChange={setFlowData}
 *   formFields={fields}
 * />
 * ```
 */

"use client"

import { useCallback, useId, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { cn } from "@/lib/utils/cn"
import { ApproverNodeConfig } from "./approver-node-config"
import { CcNodeConfig } from "./cc-node-config"
import { ConditionEditor } from "./condition-editor"
import type { ApprovalFlowBranch, ApprovalFlowNode, FormFieldDef } from "./types"

interface VerticalDesignerProps {
  value: ApprovalFlowNode
  onChange: (node: ApprovalFlowNode) => void
  /** 表单字段列表（用于条件编辑器字段选择） */
  formFields?: FormFieldDef[]
}

/** 生成唯一 ID */
function genId(): string {
  return `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 节点颜色配置 */
const NODE_COLORS: Record<string, { border: string; bg: string; text: string }> = {
  start: { border: "border-l-blue-500", bg: "bg-blue-50", text: "text-blue-700" },
  approver: { border: "border-l-orange-500", bg: "bg-orange-50", text: "text-orange-700" },
  cc: { border: "border-l-green-500", bg: "bg-green-50", text: "text-green-700" },
  condition: { border: "border-l-purple-500", bg: "bg-purple-50", text: "text-purple-700" },
  end: { border: "border-l-gray-400", bg: "bg-gray-50", text: "text-gray-600" }
}

/** 节点图标 */
const NODE_ICONS: Record<string, string> = {
  start: "📝",
  approver: "👤",
  cc: "📋",
  condition: "🔀",
  end: "⏹"
}

export function VerticalDesigner({ value, onChange, formFields = [] }: VerticalDesignerProps) {
  const formId = useId()
  const [selectedNode, setSelectedNode] = useState<ApprovalFlowNode | null>(null)

  /** 在指定节点后插入新节点 */
  const insertAfter = useCallback(
    (parentId: string, newNode: ApprovalFlowNode) => {
      function insert(node: ApprovalFlowNode): ApprovalFlowNode {
        if (node.id === parentId) {
          return { ...node, next: { ...newNode, next: node.next } }
        }
        return {
          ...node,
          next: node.next ? insert(node.next) : undefined,
          branches: node.branches?.map((b) => ({
            ...b,
            child: b.child ? insert(b.child) : undefined
          }))
        }
      }
      onChange(insert(value))
    },
    [value, onChange]
  )

  /** 删除节点 */
  const deleteNode = useCallback(
    (nodeId: string) => {
      function remove(node: ApprovalFlowNode): ApprovalFlowNode | undefined {
        if (node.id === nodeId) return node.next
        return {
          ...node,
          next: node.next ? remove(node.next) : undefined,
          branches: node.branches?.map((b) => ({
            ...b,
            child: b.child ? remove(b.child) : undefined
          }))
        } as ApprovalFlowNode
      }
      const result = remove(value)
      if (result) onChange(result)
    },
    [value, onChange]
  )

  /** 更新节点配置 */
  const updateNode = useCallback(
    (nodeId: string, config: Record<string, unknown>) => {
      function update(node: ApprovalFlowNode): ApprovalFlowNode {
        if (node.id === nodeId) return { ...node, config }
        return {
          ...node,
          next: node.next ? update(node.next) : undefined,
          branches: node.branches?.map((b) => ({
            ...b,
            child: b.child ? update(b.child) : undefined
          }))
        }
      }
      onChange(update(value))
      if (selectedNode?.id === nodeId) {
        setSelectedNode({ ...selectedNode, config })
      }
    },
    [value, onChange, selectedNode]
  )

  /** 更新分支条件 */
  const updateBranch = useCallback(
    (nodeId: string, branchId: string, patch: Partial<ApprovalFlowBranch>) => {
      function update(node: ApprovalFlowNode): ApprovalFlowNode {
        if (node.id === nodeId) {
          return {
            ...node,
            branches: node.branches?.map((b) => (b.id === branchId ? { ...b, ...patch } : b))
          }
        }
        return {
          ...node,
          next: node.next ? update(node.next) : undefined,
          branches: node.branches?.map((b) => ({
            ...b,
            child: b.child ? update(b.child) : undefined
          }))
        }
      }
      onChange(update(value))
    },
    [value, onChange]
  )

  /** 添加节点菜单 */
  function AddButton({ parentId }: { parentId: string }) {
    return (
      <div className="flex justify-center py-2">
        <div className="relative flex h-8 items-center">
          {/* 竖线 */}
          <div className="absolute top-0 left-1/2 h-full w-px -translate-x-1/2 bg-border" />
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="outline"
                  size="sm"
                  className="relative z-10 h-7 w-7 rounded-full p-0 text-lg leading-none"
                >
                  +
                </Button>
              }
            />
            <DropdownMenuContent>
              <DropdownMenuItem
                onClick={() =>
                  insertAfter(parentId, {
                    id: genId(),
                    type: "approver",
                    name: "审批人",
                    config: {},
                    next: undefined
                  })
                }
              >
                👤 审批人
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() =>
                  insertAfter(parentId, {
                    id: genId(),
                    type: "cc",
                    name: "抄送人",
                    config: { timing: "ON_APPROVE" },
                    next: undefined
                  })
                }
              >
                📋 抄送人
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() =>
                  insertAfter(parentId, {
                    id: genId(),
                    type: "condition",
                    name: "条件分支",
                    config: {},
                    branches: [
                      {
                        id: genId(),
                        name: "条件1",
                        condition: { logic: "AND", conditions: [], groups: [] }
                      },
                      { id: genId(), name: "其他", condition: undefined }
                    ],
                    next: undefined
                  })
                }
              >
                🔀 条件分支
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    )
  }

  /** 渲染单个节点卡片 */
  function NodeCard({ node }: { node: ApprovalFlowNode }) {
    const colors = NODE_COLORS[node.type] ?? NODE_COLORS.end
    const icon = NODE_ICONS[node.type] ?? "⏹"
    const canDelete = node.type !== "start" && node.type !== "end"

    /** 获取节点描述 */
    function getDescription(): string {
      if (node.type === "approver") {
        return (node.config.assignee as string) || "未配置"
      }
      if (node.type === "cc") {
        return (node.config.users as string) || "未配置"
      }
      return ""
    }

    return (
      <div className="flex justify-center">
        {/* biome-ignore lint/a11y/useSemanticElements: 需要 div 布局样式 */}
        <div
          className={cn(
            "relative w-64 cursor-pointer rounded-lg border border-l-4 p-3 shadow-sm transition-shadow hover:shadow-md",
            colors.border,
            colors.bg
          )}
          onClick={() => setSelectedNode(node)}
          onKeyDown={(e) => {
            if (e.key === "Enter") setSelectedNode(node)
          }}
          role="button"
          tabIndex={0}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span>{icon}</span>
              <span className={cn("font-medium text-sm", colors.text)}>{node.name}</span>
            </div>
            {canDelete && (
              <Button
                variant="ghost"
                size="sm"
                className="h-5 w-5 p-0 text-muted-foreground hover:text-destructive"
                onClick={(e) => {
                  e.stopPropagation()
                  deleteNode(node.id)
                }}
              >
                ×
              </Button>
            )}
          </div>
          {getDescription() && (
            <p className="mt-1 truncate text-muted-foreground text-xs">{getDescription()}</p>
          )}
        </div>
      </div>
    )
  }

  /** 渲染条件分支 */
  function ConditionBranches({ node }: { node: ApprovalFlowNode }) {
    if (!node.branches?.length) return null
    return (
      <div className="flex justify-center">
        <div className="rounded-lg border border-l-4 border-l-purple-500 bg-purple-50 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="font-medium text-purple-700 text-sm">🔀 {node.name}</span>
            <Button
              variant="ghost"
              size="sm"
              className="h-5 w-5 p-0 text-muted-foreground hover:text-destructive"
              onClick={() => deleteNode(node.id)}
            >
              ×
            </Button>
          </div>
          <div className="flex gap-4">
            {node.branches.map((branch) => (
              <div key={branch.id} className="min-w-[180px] rounded border bg-white p-2">
                <button
                  type="button"
                  className="mb-2 w-full text-left font-medium text-purple-600 text-xs hover:underline"
                  onClick={() => setSelectedNode({ ...node, config: { _editBranch: branch.id } })}
                >
                  {branch.name}
                </button>
                {branch.child && <NodeChain node={branch.child} />}
                {!branch.child && (
                  <div className="flex justify-center py-1">
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-6 w-6 rounded-full p-0 text-xs"
                          >
                            +
                          </Button>
                        }
                      />
                      <DropdownMenuContent>
                        <DropdownMenuItem
                          onClick={() => {
                            updateBranch(node.id, branch.id, {
                              child: { id: genId(), type: "approver", name: "审批人", config: {} }
                            })
                          }}
                        >
                          👤 审批人
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => {
                            updateBranch(node.id, branch.id, {
                              child: {
                                id: genId(),
                                type: "cc",
                                name: "抄送人",
                                config: { timing: "ON_APPROVE" }
                              }
                            })
                          }}
                        >
                          📋 抄送人
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  /** 递归渲染节点链 */
  function NodeChain({ node }: { node: ApprovalFlowNode }) {
    return (
      <div className="flex flex-col items-center">
        {node.type === "condition" ? <ConditionBranches node={node} /> : <NodeCard node={node} />}
        {node.next && node.type !== "end" && (
          <>
            <AddButton parentId={node.id} />
            <NodeChain node={node.next} />
          </>
        )}
        {!node.next && node.type !== "end" && <AddButton parentId={node.id} />}
      </div>
    )
  }

  /** 配置面板内容 */
  function ConfigContent() {
    if (!selectedNode) return null

    // 条件分支编辑
    if (selectedNode.type === "condition" && selectedNode.config._editBranch) {
      const branch = selectedNode.branches?.find((b) => b.id === selectedNode.config._editBranch)
      if (!branch) return null
      return (
        <div className="space-y-4">
          <div>
            <label htmlFor={`${formId}-branch-name`} className="font-medium text-sm">
              分支名称
            </label>
            <input
              id={`${formId}-branch-name`}
              className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
              value={branch.name}
              onChange={(e) => updateBranch(selectedNode.id, branch.id, { name: e.target.value })}
            />
          </div>
          {branch.condition !== undefined && (
            <ConditionEditor
              value={branch.condition}
              onChange={(condition) => updateBranch(selectedNode.id, branch.id, { condition })}
              fields={formFields}
            />
          )}
        </div>
      )
    }

    if (selectedNode.type === "approver") {
      return (
        <ApproverNodeConfig
          config={selectedNode.config}
          onChange={(config) => updateNode(selectedNode.id, config)}
        />
      )
    }

    if (selectedNode.type === "cc") {
      return (
        <CcNodeConfig
          config={selectedNode.config}
          onChange={(config) => updateNode(selectedNode.id, config)}
        />
      )
    }

    return <p className="text-muted-foreground text-sm">该节点无可配置属性</p>
  }

  return (
    <div className="relative flex h-full">
      {/* 主画布 */}
      <div className="flex-1 overflow-auto p-8">
        <NodeChain node={value} />
      </div>

      {/* 右侧配置面板 */}
      <Sheet
        open={selectedNode !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedNode(null)
        }}
      >
        <SheetContent className="w-[380px] sm:w-[420px]">
          <SheetHeader>
            <SheetTitle>
              {selectedNode
                ? `${NODE_ICONS[selectedNode.type] ?? ""} ${selectedNode.name}`
                : "配置"}
            </SheetTitle>
          </SheetHeader>
          <div className="mt-4">
            <ConfigContent />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
