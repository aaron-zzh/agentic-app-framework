/**
 * 私有角色编辑对话框：名称、描述、技能与工具选择。
 *
 * 技能只列出当前用户可见且已发布的技能（不含工作区技能），工具只列出平台允许选择的工具；
 * 保存时服务端会再次校验，越权引用的技能或工具名会原样显示在对话框内。
 * @author AaronZZH & Kiro
 */

"use client"

import { useId, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { AiRoleSkillBinding, AiRoleVO, MyAiRoleInput } from "@/lib/api/rest/ai/role"
import { useAiRoleToolOptions, useCreateMyAiRole, useUpdateMyAiRole } from "@/lib/api/rest/ai/role"
import { useAiSkills } from "@/lib/api/rest/ai/skill"

interface MyRoleEditorDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 为空表示新建 */
  role: AiRoleVO | null
}

/** 解析角色上的工具授权池 JSON，非法时按空处理。 */
function parseToolNames(toolWhitelist: string | null): string[] {
  if (!toolWhitelist) return []
  try {
    const parsed: unknown = JSON.parse(toolWhitelist)
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === "string")
      : []
  } catch {
    return []
  }
}

/** 从接口错误中取出可展示的文案。 */
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "保存失败，请重试"
}

export function MyRoleEditorDialog({ open, onOpenChange, role }: MyRoleEditorDialogProps) {
  // 以 key 重置内部表单状态：每次打开或切换角色都重新挂载
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <MyRoleEditorForm key={role?.id ?? "new"} role={role} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}

function MyRoleEditorForm({ role, onDone }: { role: AiRoleVO | null; onDone: () => void }) {
  const uid = useId()
  const [name, setName] = useState(role?.name ?? "")
  const [description, setDescription] = useState(role?.description ?? "")
  const [skills, setSkills] = useState<AiRoleSkillBinding[]>(role?.skillBindings ?? [])
  const [tools, setTools] = useState<string[]>(parseToolNames(role?.toolWhitelist ?? null))
  const [error, setError] = useState<string | null>(null)

  const { data: visibleSkills = [] } = useAiSkills({ publishedOnly: true })
  const { data: toolOptions = [] } = useAiRoleToolOptions()
  const createRole = useCreateMyAiRole()
  const updateRole = useUpdateMyAiRole()
  const saving = createRole.isPending || updateRole.isPending

  // 工作区技能不支持被角色引用
  const selectableSkills = visibleSkills.filter((skill) => skill.visibility !== "WORKSPACE")

  const toggleSkill = (skillKey: string, checked: boolean) => {
    setSkills((prev) =>
      checked
        ? [...prev, { skillKey, activationMode: "ON_DEMAND" }]
        : prev.filter((item) => item.skillKey !== skillKey)
    )
  }

  const setAlways = (skillKey: string, always: boolean) => {
    setSkills((prev) =>
      prev.map((item) =>
        item.skillKey === skillKey
          ? { ...item, activationMode: always ? "ALWAYS" : "ON_DEMAND" }
          : item
      )
    )
  }

  const toggleTool = (toolName: string, checked: boolean) => {
    setTools((prev) => (checked ? [...prev, toolName] : prev.filter((item) => item !== toolName)))
  }

  const handleSave = async () => {
    if (!name.trim()) {
      setError("请填写角色名称")
      return
    }
    setError(null)
    const input: MyAiRoleInput = {
      name: name.trim(),
      description: description.trim() || undefined,
      skillBindings: skills,
      toolWhitelist: JSON.stringify(tools)
    }
    try {
      if (role) {
        await updateRole.mutateAsync({ id: role.id, ...input })
      } else {
        await createRole.mutateAsync(input)
      }
      onDone()
    } catch (failure) {
      // 越权引用时服务端返回具体的技能 / 工具名，原样显示在对话框内
      setError(errorMessage(failure))
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{role ? "编辑私有角色" : "新建私有角色"}</DialogTitle>
        <DialogDescription>
          私有角色仅你自己可见和绑定，可引用你可见的技能与平台允许的工具。
        </DialogDescription>
      </DialogHeader>

      <ScrollArea className="max-h-[60vh]">
        <div className="space-y-4 py-2 pr-3">
          <div className="space-y-2">
            <Label htmlFor={`${uid}-name`}>名称</Label>
            <Input
              id={`${uid}-name`}
              value={name}
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${uid}-desc`}>描述</Label>
            <Textarea
              id={`${uid}-desc`}
              value={description}
              maxLength={512}
              placeholder="用一两句话说明这个角色适合处理什么任务，助理会据此选择角色"
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>

          <fieldset className="space-y-2">
            <legend className="font-medium text-sm">技能（{skills.length}）</legend>
            {selectableSkills.length === 0 ? (
              <p className="text-muted-foreground text-sm">暂无可引用的技能</p>
            ) : (
              <ul className="space-y-2">
                {selectableSkills.map((skill) => {
                  const selected = skills.find((item) => item.skillKey === skill.code)
                  return (
                    <li key={skill.code} className="flex items-start gap-3 rounded-md border p-2">
                      <Checkbox
                        checked={selected !== undefined}
                        aria-label={`选择技能 ${skill.name}`}
                        onCheckedChange={(checked) => toggleSkill(skill.code, checked === true)}
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-sm">{skill.name}</p>
                        <p className="line-clamp-2 text-muted-foreground text-xs">
                          {skill.summary}
                        </p>
                        <p className="text-muted-foreground text-xs">{skill.code}</p>
                      </div>
                      {selected && (
                        <div className="flex items-center gap-2 text-xs">
                          <span>始终激活</span>
                          <Switch
                            checked={selected.activationMode === "ALWAYS"}
                            aria-label={`${skill.name} 始终激活`}
                            onCheckedChange={(checked) => setAlways(skill.code, checked)}
                          />
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="font-medium text-sm">工具（{tools.length}）</legend>
            {toolOptions.length === 0 ? (
              <p className="text-muted-foreground text-sm">暂无可选择的工具</p>
            ) : (
              <ul className="grid gap-2 sm:grid-cols-2">
                {toolOptions.map((tool) => (
                  <li key={tool.name} className="flex items-start gap-3 rounded-md border p-2">
                    <Checkbox
                      checked={tools.includes(tool.name)}
                      aria-label={`选择工具 ${tool.name}`}
                      onCheckedChange={(checked) => toggleTool(tool.name, checked === true)}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm">{tool.name}</p>
                      <div className="flex flex-wrap gap-1 pt-1">
                        {tool.riskLevel && <Badge variant="outline">{tool.riskLevel}</Badge>}
                        {tool.requireConfirm && <Badge variant="secondary">需确认</Badge>}
                        {tool.readOnly && <Badge variant="secondary">只读</Badge>}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </fieldset>
        </div>
      </ScrollArea>

      {error && (
        <p role="alert" className="rounded-md bg-destructive/10 p-2 text-destructive text-sm">
          {error}
        </p>
      )}

      <DialogFooter>
        <Button variant="outline" onClick={onDone} disabled={saving}>
          取消
        </Button>
        <Button onClick={handleSave} disabled={saving}>
          {saving ? "保存中..." : "保存"}
        </Button>
      </DialogFooter>
    </>
  )
}
