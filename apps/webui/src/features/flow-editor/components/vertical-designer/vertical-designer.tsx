/**
 * 竖向审批设计器——钉钉/飞书风格卡片式布局
 * @author AaronZZH
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
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { ApproverNodeConfig } from "./approver-node-config"
import { CcNodeConfig } from "./cc-node-config"
import { ConditionEditor } from "./condition-editor"
import { DesignerCanvas } from "./DesignerCanvas"
import { NODE_ICONS } from "./DesignerNode"
import type { ApprovalFlowBranch, ApprovalFlowNode, FormFieldDef } from "./types"

interface VerticalDesignerProps {
  value: ApprovalFlowNode
  onChange: (node: ApprovalFlowNode) => void
  /** 表单字段列表（用于条件编辑器字段选择） */
  formFields?: FormFieldDef[]
}

export function VerticalDesigner({ value, onChange, formFields = [] }: VerticalDesignerProps) {
  const formId = useId()
  const [selectedNode, setSelectedNode] = useState<ApprovalFlowNode | null>(null)

  /** 在指定节点后插入新节点 */
  const insertAfter = useCallback(
    (parentId: string, newNode: ApprovalFlowNode) => {
      function insert(node: ApprovalFlowNode): ApprovalFlowNode {
        if (node.id === parentId) return { ...node, next: { ...newNode, next: node.next } }
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
      if (selectedNode?.id === nodeId) setSelectedNode({ ...selectedNode, config })
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

  /** 配置面板内容 */
  function ConfigContent() {
    if (!selectedNode) return null

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
      <DesignerCanvas
        root={value}
        onSelectNode={setSelectedNode}
        onDeleteNode={deleteNode}
        onInsertAfter={insertAfter}
        onUpdateBranch={updateBranch}
      />

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
