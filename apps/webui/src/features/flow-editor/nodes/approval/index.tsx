/**
 * 审批工作流节点集——开始/结束/用户任务/服务任务/网关/子流程
 * @author AaronZZH & Kiro
 */

"use client"

import { memo } from "react"
import { Handle, Position, type NodeProps } from "@xyflow/react"
import { cn } from "@/lib/utils/cn"
import type { NodeTypeDef, NodeTypeRegistry, InspectorProps, PortDef } from "../../types"
import { BaseNode } from "../_base/base-node"
import { createNodeRegistry, registerNodeType, setApprovalRegistry } from "../../lib/registry"

/** 开始节点 */
const StartNode = memo(function StartNode(props: NodeProps) {
  return (
    <div className={cn("flex h-10 w-10 items-center justify-center rounded-full border-2 border-green-500 bg-green-50", props.selected && "ring-2 ring-primary")}>
      <Handle id="out" type="source" position={Position.Bottom} className="!bg-green-500 !h-2 !w-2" />
      <span className="text-xs font-bold text-green-700">开始</span>
    </div>
  )
})

/** 结束节点 */
const EndNode = memo(function EndNode(props: NodeProps) {
  return (
    <div className={cn("flex h-10 w-10 items-center justify-center rounded-full border-2 border-red-500 bg-red-50", props.selected && "ring-2 ring-primary")}>
      <Handle id="in" type="target" position={Position.Top} className="!bg-red-500 !h-2 !w-2" />
      <span className="text-xs font-bold text-red-700">结束</span>
    </div>
  )
})

/** 网关节点（菱形） */
const GatewayNode = memo(function GatewayNode(props: NodeProps) {
  return (
    <div className={cn("flex h-12 w-12 rotate-45 items-center justify-center border-2 border-amber-500 bg-amber-50", props.selected && "ring-2 ring-primary")}>
      <Handle id="in" type="target" position={Position.Top} className="!bg-amber-500 !h-2 !w-2 !-rotate-45" />
      <Handle id="out-yes" type="source" position={Position.Right} className="!bg-amber-500 !h-2 !w-2 !-rotate-45" />
      <Handle id="out-no" type="source" position={Position.Bottom} className="!bg-amber-500 !h-2 !w-2 !-rotate-45" />
      <span className="-rotate-45 text-xs font-bold text-amber-700">×</span>
    </div>
  )
})

/** 空属性面板（占位） */
function EmptyInspector(_props: InspectorProps) {
  return <p className="text-muted-foreground text-sm">该节点无可配置属性</p>
}

/** 审批人策略选项 */
const ASSIGNEE_STRATEGIES = [
  { value: "FIXED_USER", label: "指定用户" },
  { value: "ROLE", label: "角色" },
  { value: "DEPARTMENT_HEAD", label: "部门主管" },
  { value: "INITIATOR_SELECT", label: "发起人选择" },
  { value: "EXPRESSION", label: "表达式" }
]

/** 超时策略选项 */
const TIMEOUT_STRATEGIES = [
  { value: "AUTO_APPROVE", label: "自动通过" },
  { value: "AUTO_REJECT", label: "自动驳回" },
  { value: "TRANSFER", label: "转交" },
  { value: "REMIND", label: "提醒" }
]

/** 空审批人策略选项 */
const EMPTY_ASSIGNEE_STRATEGIES = [
  { value: "SKIP", label: "跳过" },
  { value: "ADMIN", label: "转管理员" },
  { value: "ERROR", label: "报错" }
]

/** 用户任务属性面板（增强版：审批人策略 + 超时 + 空审批人） */
function UserTaskInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="taskName" className="text-sm font-medium">任务名称</label>
        <input
          id="taskName"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.taskName as string) ?? ""}
          onChange={(e) => onChange({ ...data, taskName: e.target.value })}
          placeholder="审批任务名称"
        />
      </div>
      <div>
        <label htmlFor="assigneeStrategy" className="text-sm font-medium">审批人策略</label>
        <select
          id="assigneeStrategy"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.assigneeStrategy as string) ?? "FIXED_USER"}
          onChange={(e) => onChange({ ...data, assigneeStrategy: e.target.value })}
        >
          {ASSIGNEE_STRATEGIES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="assignee" className="text-sm font-medium">审批人</label>
        <input
          id="assignee"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.assignee as string) ?? ""}
          onChange={(e) => onChange({ ...data, assignee: e.target.value })}
          placeholder={data.assigneeStrategy === "EXPRESSION" ? "${expression}" : "输入用户名或角色"}
        />
      </div>
      <div>
        <label htmlFor="timeoutStrategy" className="text-sm font-medium">超时策略</label>
        <select
          id="timeoutStrategy"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.timeoutStrategy as string) ?? "REMIND"}
          onChange={(e) => onChange({ ...data, timeoutStrategy: e.target.value })}
        >
          {TIMEOUT_STRATEGIES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="emptyAssigneeStrategy" className="text-sm font-medium">空审批人处理</label>
        <select
          id="emptyAssigneeStrategy"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.emptyAssigneeStrategy as string) ?? "ERROR"}
          onChange={(e) => onChange({ ...data, emptyAssigneeStrategy: e.target.value })}
        >
          {EMPTY_ASSIGNEE_STRATEGIES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>
    </div>
  )
}

/** 服务任务属性面板 */
function ServiceTaskInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="serviceClass" className="text-sm font-medium">服务类</label>
        <input
          id="serviceClass"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.serviceClass as string) ?? ""}
          onChange={(e) => onChange({ ...data, serviceClass: e.target.value })}
          placeholder="com.example.Service"
        />
      </div>
    </div>
  )
}

/** 会签模式选项 */
const COUNTERSIGN_MODES = [
  { value: "ALL_APPROVE", label: "全部通过" },
  { value: "ANY_APPROVE", label: "任一通过" },
  { value: "RATIO", label: "比例通过" }
]

/** 会签节点属性面板 */
function CountersignInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="countersignMode" className="text-sm font-medium">会签模式</label>
        <select
          id="countersignMode"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.countersignMode as string) ?? "ALL_APPROVE"}
          onChange={(e) => onChange({ ...data, countersignMode: e.target.value })}
        >
          {COUNTERSIGN_MODES.map((m) => (
            <option key={m.value} value={m.value}>{m.label}</option>
          ))}
        </select>
      </div>
      {data.countersignMode === "RATIO" && (
        <div>
          <label htmlFor="passRatio" className="text-sm font-medium">
            通过率：{((data.passRatio as number) ?? 50)}%
          </label>
          <input
            id="passRatio"
            type="range"
            min={1}
            max={100}
            className="mt-1 w-full"
            value={(data.passRatio as number) ?? 50}
            onChange={(e) => onChange({ ...data, passRatio: Number(e.target.value) })}
          />
        </div>
      )}
      <div>
        <label htmlFor="assignees" className="text-sm font-medium">审批人列表</label>
        <input
          id="assignees"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(data.assignees as string) ?? ""}
          onChange={(e) => onChange({ ...data, assignees: e.target.value })}
          placeholder="逗号分隔，如：user1,user2,user3"
        />
        <p className="text-muted-foreground mt-1 text-xs">多个审批人用逗号分隔</p>
      </div>
    </div>
  )
}

/** 网关属性面板 */
function GatewayInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="condition" className="text-sm font-medium">条件表达式</label>
        <input
          id="condition"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm font-mono"
          value={(data.condition as string) ?? ""}
          onChange={(e) => onChange({ ...data, condition: e.target.value })}
          placeholder="${amount > 1000}"
        />
      </div>
    </div>
  )
}

/** 通用端口定义 */
const inputOutput: PortDef[] = [
  { id: "in", direction: "input" },
  { id: "out", direction: "output" }
]

/** 构建审批节点注册表 */
function buildApprovalRegistry(): NodeTypeRegistry {
  let registry = createNodeRegistry()

  registry = registerNodeType(registry, "start", {
    component: StartNode,
    inspector: EmptyInspector,
    icon: "▶",
    label: "开始",
    category: "trigger",
    ports: [{ id: "out", direction: "output" }],
    defaultData: { label: "开始" }
  })

  registry = registerNodeType(registry, "end", {
    component: EndNode,
    inspector: EmptyInspector,
    icon: "⏹",
    label: "结束",
    category: "output",
    ports: [{ id: "in", direction: "input" }],
    defaultData: { label: "结束" }
  })

  registry = registerNodeType(registry, "userTask", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: UserTaskInspector,
    icon: "👤",
    label: "用户任务",
    category: "interact",
    ports: inputOutput,
    defaultData: { label: "用户任务", icon: "👤", ports: inputOutput, assignee: "", taskName: "" }
  })

  registry = registerNodeType(registry, "serviceTask", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: ServiceTaskInspector,
    icon: "⚙️",
    label: "服务任务",
    category: "tool",
    ports: inputOutput,
    defaultData: { label: "服务任务", icon: "⚙️", ports: inputOutput, serviceClass: "" }
  })

  registry = registerNodeType(registry, "gateway", {
    component: GatewayNode,
    inspector: GatewayInspector,
    icon: "◇",
    label: "排他网关",
    category: "logic",
    ports: [
      { id: "in", direction: "input" },
      { id: "out-yes", direction: "output" },
      { id: "out-no", direction: "output" }
    ],
    defaultData: { label: "网关", condition: "" }
  })

  registry = registerNodeType(registry, "subProcess", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "📦",
    label: "子流程",
    category: "logic",
    ports: inputOutput,
    defaultData: { label: "子流程", icon: "📦", ports: inputOutput }
  })

  registry = registerNodeType(registry, "countersign", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: CountersignInspector,
    icon: "👥",
    label: "会签",
    category: "interact",
    ports: inputOutput,
    defaultData: {
      label: "会签",
      icon: "👥",
      ports: inputOutput,
      countersignMode: "ALL_APPROVE",
      passRatio: 50,
      assignees: ""
    }
  })

  return registry
}

export const approvalNodeRegistry = buildApprovalRegistry()
setApprovalRegistry(approvalNodeRegistry)
