/**
 * 审批人节点配置面板——审批人策略 + 指定人员
 * @author Kiro
 */

"use client"

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

interface ApproverNodeConfigProps {
  config: Record<string, unknown>
  onChange: (config: Record<string, unknown>) => void
}

/** 审批人策略选项 */
const STRATEGIES = [
  { value: "FIXED_USER", label: "指定用户" },
  { value: "ROLE", label: "角色" },
  { value: "DEPARTMENT_HEAD", label: "部门主管" },
  { value: "INITIATOR_SELECT", label: "发起人选择" }
]

export function ApproverNodeConfig({ config, onChange }: ApproverNodeConfigProps) {
  const strategy = (config.strategy as string) ?? "FIXED_USER"

  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="approver-strategy" className="text-sm font-medium">审批人策略</label>
        <Select value={strategy} onValueChange={(v) => onChange({ ...config, strategy: v })}>
          <SelectTrigger id="approver-strategy" className="mt-1">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STRATEGIES.map((s) => (
              <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div>
        <label htmlFor="approver-assignee" className="text-sm font-medium">
          {strategy === "ROLE" ? "角色名称" : "审批人"}
        </label>
        <input
          id="approver-assignee"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(config.assignee as string) ?? ""}
          onChange={(e) => onChange({ ...config, assignee: e.target.value })}
          placeholder={strategy === "ROLE" ? "输入角色名称" : "输入用户名"}
        />
      </div>
    </div>
  )
}
