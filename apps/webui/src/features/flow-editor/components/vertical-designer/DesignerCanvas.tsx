/**
 * 竖向设计器——画布容器（节点链递归渲染 + 添加按钮 + 条件分支）
 * @author Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { DesignerNode, NODE_ICONS } from "./DesignerNode"
import type { ApprovalFlowBranch, ApprovalFlowNode } from "./types"

interface DesignerCanvasProps {
  root: ApprovalFlowNode
  onSelectNode: (node: ApprovalFlowNode) => void
  onDeleteNode: (nodeId: string) => void
  onInsertAfter: (parentId: string, newNode: ApprovalFlowNode) => void
  onUpdateBranch: (nodeId: string, branchId: string, patch: Partial<ApprovalFlowBranch>) => void
}

/** 生成唯一 ID */
function genId(): string {
  return `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 添加节点菜单 */
function AddButton({ parentId, onInsert }: { parentId: string; onInsert: (parentId: string, node: ApprovalFlowNode) => void }) {
  return (
    <div className="flex justify-center py-2">
      <div className="relative flex h-8 items-center">
        <div className="absolute top-0 left-1/2 h-full w-px -translate-x-1/2 bg-border" />
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button variant="outline" size="sm" className="relative z-10 h-7 w-7 rounded-full p-0 text-lg leading-none">
                +
              </Button>
            }
          />
          <DropdownMenuContent>
            <DropdownMenuItem onClick={() => onInsert(parentId, { id: genId(), type: "approver", name: "审批人", config: {}, next: undefined })}>
              👤 审批人
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => onInsert(parentId, { id: genId(), type: "cc", name: "抄送人", config: { timing: "ON_APPROVE" }, next: undefined })}>
              📋 抄送人
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => onInsert(parentId, {
              id: genId(), type: "condition", name: "条件分支", config: {},
              branches: [
                { id: genId(), name: "条件1", condition: { logic: "AND", conditions: [], groups: [] } },
                { id: genId(), name: "其他", condition: undefined }
              ],
              next: undefined
            })}>
              🔀 条件分支
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}

/** 条件分支渲染 */
function ConditionBranches({
  node,
  onSelectNode,
  onDeleteNode,
  onInsertAfter,
  onUpdateBranch
}: DesignerCanvasProps & { node: ApprovalFlowNode }) {
  if (!node.branches?.length) return null
  return (
    <div className="flex justify-center">
      <div className="rounded-lg border border-l-4 border-l-purple-500 bg-purple-50 p-3">
        <div className="mb-2 flex items-center justify-between">
          <span className="font-medium text-purple-700 text-sm">{NODE_ICONS.condition} {node.name}</span>
          <Button
            variant="ghost"
            size="sm"
            className="h-5 w-5 p-0 text-muted-foreground hover:text-destructive"
            onClick={() => onDeleteNode(node.id)}
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
                onClick={() => onSelectNode({ ...node, config: { _editBranch: branch.id } })}
              >
                {branch.name}
              </button>
              {branch.child && (
                <NodeChain
                  node={branch.child}
                  onSelectNode={onSelectNode}
                  onDeleteNode={onDeleteNode}
                  onInsertAfter={onInsertAfter}
                  onUpdateBranch={onUpdateBranch}
                />
              )}
              {!branch.child && (
                <div className="flex justify-center py-1">
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      render={<Button variant="outline" size="sm" className="h-6 w-6 rounded-full p-0 text-xs">+</Button>}
                    />
                    <DropdownMenuContent>
                      <DropdownMenuItem onClick={() => onUpdateBranch(node.id, branch.id, { child: { id: genId(), type: "approver", name: "审批人", config: {} } })}>
                        👤 审批人
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => onUpdateBranch(node.id, branch.id, { child: { id: genId(), type: "cc", name: "抄送人", config: { timing: "ON_APPROVE" } } })}>
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
function NodeChain({
  node,
  onSelectNode,
  onDeleteNode,
  onInsertAfter,
  onUpdateBranch
}: { node: ApprovalFlowNode } & Omit<DesignerCanvasProps, "root">) {
  return (
    <div className="flex flex-col items-center">
      {node.type === "condition" ? (
        <ConditionBranches root={node} node={node} onSelectNode={onSelectNode} onDeleteNode={onDeleteNode} onInsertAfter={onInsertAfter} onUpdateBranch={onUpdateBranch} />
      ) : (
        <DesignerNode node={node} onSelect={onSelectNode} onDelete={onDeleteNode} />
      )}
      {node.next && node.type !== "end" && (
        <>
          <AddButton parentId={node.id} onInsert={onInsertAfter} />
          <NodeChain node={node.next} onSelectNode={onSelectNode} onDeleteNode={onDeleteNode} onInsertAfter={onInsertAfter} onUpdateBranch={onUpdateBranch} />
        </>
      )}
      {!node.next && node.type !== "end" && <AddButton parentId={node.id} onInsert={onInsertAfter} />}
    </div>
  )
}

/** 画布容器 */
export function DesignerCanvas({ root, onSelectNode, onDeleteNode, onInsertAfter, onUpdateBranch }: DesignerCanvasProps) {
  return (
    <div className="flex-1 overflow-auto p-8">
      <NodeChain node={root} onSelectNode={onSelectNode} onDeleteNode={onDeleteNode} onInsertAfter={onInsertAfter} onUpdateBranch={onUpdateBranch} />
    </div>
  )
}
