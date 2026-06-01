"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { request } from "@/lib/api/rest/entity/crud"

interface AgentVO {
  id: number
  agentId: string
  name: string
  description: string
  systemPrompt: string
  modelId: string
  capabilities: string
  tools: string
  mcpServers: string
  maxIterations: number
  timeoutSeconds: number
  status: string
  createTime: string
}

function useAgents() {
  return useQuery<AgentVO[]>({
    queryKey: ["agents"],
    queryFn: () => request<AgentVO[]>("/ai/agents")
  })
}

export default function AgentManagementPage() {
  const { data: agents, isLoading } = useAgents()
  const [editAgent, setEditAgent] = useState<AgentVO | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  if (isLoading) {
    return <div className="p-6 text-muted-foreground">加载中...</div>
  }

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-xl">Agent 管理</h1>
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogTrigger render={<Button size="sm"><Plus className="mr-1 size-4" />新建 Agent</Button>} />
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>新建 Agent</DialogTitle>
            </DialogHeader>
            <AgentForm onSuccess={() => setCreateOpen(false)} />
          </DialogContent>
        </Dialog>
      </div>

      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        {agents?.map((agent) => (
          <AgentCard key={agent.id} agent={agent} onEdit={() => setEditAgent(agent)} />
        ))}
        {agents?.length === 0 && (
          <p className="col-span-full text-center text-muted-foreground">暂无 Agent，点击新建</p>
        )}
      </div>

      {/* 编辑弹窗 */}
      <Dialog open={!!editAgent} onOpenChange={(open) => !open && setEditAgent(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>编辑 Agent</DialogTitle>
          </DialogHeader>
          {editAgent && (
            <AgentForm agent={editAgent} onSuccess={() => setEditAgent(null)} />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function AgentCard({ agent, onEdit }: { agent: AgentVO; onEdit: () => void }) {
  return (
    <div className="rounded-lg border p-4 transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-medium text-sm">{agent.name}</h3>
          <p className="mt-0.5 text-muted-foreground text-xs">{agent.agentId}</p>
        </div>
        <span
          className={`rounded-full px-2 py-0.5 text-xs ${
            agent.status === "active"
              ? "bg-green-100 text-green-700"
              : "bg-gray-100 text-gray-600"
          }`}
        >
          {agent.status === "active" ? "活跃" : "停用"}
        </span>
      </div>
      {agent.description && (
        <p className="mt-2 line-clamp-2 text-muted-foreground text-xs">{agent.description}</p>
      )}
      <div className="mt-3 flex items-center justify-between text-xs">
        <span className="text-muted-foreground">模型: {agent.modelId ?? "默认"}</span>
        <Button variant="ghost" size="sm" onClick={onEdit}>
          编辑
        </Button>
      </div>
    </div>
  )
}

function AgentForm({ agent, onSuccess }: { agent?: AgentVO; onSuccess: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    agentId: agent?.agentId ?? "",
    name: agent?.name ?? "",
    description: agent?.description ?? "",
    systemPrompt: agent?.systemPrompt ?? "",
    modelId: agent?.modelId ?? "",
    capabilities: agent?.capabilities ?? "",
    tools: agent?.tools ?? "",
    mcpServers: agent?.mcpServers ?? "",
    maxIterations: agent?.maxIterations ?? 10,
    timeoutSeconds: agent?.timeoutSeconds ?? 120
  })

  const mutation = useMutation({
    mutationFn: (data: typeof form) =>
      agent
        ? request(`/ai/agents/${agent.id}`, { method: "PUT", body: JSON.stringify(data) })
        : request("/ai/agents", { method: "POST", body: JSON.stringify(data) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agents"] })
      onSuccess()
    }
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate(form)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <Field label="Agent ID" value={form.agentId} disabled={!!agent}
          onChange={(v) => setForm((f) => ({ ...f, agentId: v }))} />
        <Field label="名称" value={form.name}
          onChange={(v) => setForm((f) => ({ ...f, name: v }))} />
      </div>
      <Field label="描述" value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))} />
      <Field label="系统提示词" value={form.systemPrompt} multiline
        onChange={(v) => setForm((f) => ({ ...f, systemPrompt: v }))} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="模型 ID" value={form.modelId}
          onChange={(v) => setForm((f) => ({ ...f, modelId: v }))} />
        <Field label="能力（JSON 数组）" value={form.capabilities}
          onChange={(v) => setForm((f) => ({ ...f, capabilities: v }))} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="工具列表（JSON 数组）" value={form.tools}
          onChange={(v) => setForm((f) => ({ ...f, tools: v }))} />
        <Field label="MCP 服务器（JSON 数组）" value={form.mcpServers}
          onChange={(v) => setForm((f) => ({ ...f, mcpServers: v }))} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="最大迭代" value={String(form.maxIterations)} type="number"
          onChange={(v) => setForm((f) => ({ ...f, maxIterations: Number(v) }))} />
        <Field label="超时（秒）" value={String(form.timeoutSeconds)} type="number"
          onChange={(v) => setForm((f) => ({ ...f, timeoutSeconds: Number(v) }))} />
      </div>
      <div className="flex justify-end pt-2">
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? "保存中..." : "保存"}
        </Button>
      </div>
    </form>
  )
}

function Field({
  label, value, onChange, multiline, disabled, type = "text"
}: {
  label: string
  value: string
  onChange: (v: string) => void
  multiline?: boolean
  disabled?: boolean
  type?: string
}) {
  const cls = "w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring"
  return (
    <div>
      <label className="mb-1 block text-muted-foreground text-xs">{label}</label>
      {multiline ? (
        <textarea className={`${cls} min-h-[80px] resize-y`} value={value}
          onChange={(e) => onChange(e.target.value)} disabled={disabled} />
      ) : (
        <input className={cls} type={type} value={value}
          onChange={(e) => onChange(e.target.value)} disabled={disabled} />
      )}
    </div>
  )
}
