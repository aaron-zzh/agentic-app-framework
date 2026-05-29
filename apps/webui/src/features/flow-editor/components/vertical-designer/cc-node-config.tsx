/**
 * 抄送节点配置面板——抄送人选择 + 抄送时机
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
import type { CcTiming } from "./types"

interface CcNodeConfigProps {
  config: Record<string, unknown>
  onChange: (config: Record<string, unknown>) => void
}

/** 抄送时机选项 */
const CC_TIMING_OPTIONS: { value: CcTiming; label: string }[] = [
  { value: "ON_APPROVE", label: "审批通过后" },
  { value: "ON_SUBMIT", label: "审批发起时" }
]

export function CcNodeConfig({ config, onChange }: CcNodeConfigProps) {
  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="cc-users" className="font-medium text-sm">
          抄送人
        </label>
        <input
          id="cc-users"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(config.users as string) ?? ""}
          onChange={(e) => onChange({ ...config, users: e.target.value })}
          placeholder="输入用户名或角色，逗号分隔"
        />
        <p className="mt-1 text-muted-foreground text-xs">多个抄送人用逗号分隔</p>
      </div>

      <div>
        <label htmlFor="cc-timing" className="font-medium text-sm">
          抄送时机
        </label>
        <Select
          value={(config.timing as string) ?? "ON_APPROVE"}
          onValueChange={(v) => onChange({ ...config, timing: v })}
        >
          <SelectTrigger id="cc-timing" className="mt-1">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {CC_TIMING_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  )
}
