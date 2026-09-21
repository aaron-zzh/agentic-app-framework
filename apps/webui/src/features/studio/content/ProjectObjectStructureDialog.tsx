/**
 * 项目对象结构动作：单一 Dialog 承载追加、合同更新与移除。
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useId, useState } from "react"
import { Button } from "@/components/ui/button"
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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type {
  AigcContractRole,
  AigcProject,
  AigcProjectObject,
  AigcProjectSlotTemplateSpec
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectConfiguration,
  useAppendAigcProjectObject,
  useRemoveAigcProjectObject,
  useUpdateAigcProjectObjectContract
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"
import { getObjectTypeConfig } from "./project-type-config"

export type ProjectObjectStructureAction =
  | { kind: "append"; parent?: AigcProjectObject }
  | { kind: "contract"; object: AigcProjectObject }
  | { kind: "remove"; object: AigcProjectObject }

interface ProjectObjectStructureDialogProps {
  action: ProjectObjectStructureAction | null
  project: AigcProject
  objects: AigcProjectObject[]
  mutable: boolean
  onOpenChange: (open: boolean) => void
}

const CONTRACT_ROLES: AigcContractRole[] = ["REQUIRED", "OPTIONAL", "EXCLUDED"]

export function ProjectObjectStructureDialog({
  action,
  project,
  objects,
  mutable,
  onOpenChange
}: ProjectObjectStructureDialogProps) {
  const displayNameId = useId()
  const objectTypeId = useId()
  const parentSelectId = useId()
  const contractRoleId = useId()
  const reasonId = useId()
  const [displayName, setDisplayName] = useState("")
  const [templateKey, setTemplateKey] = useState<string | undefined>()
  const [contractRole, setContractRole] = useState<AigcContractRole>("OPTIONAL")
  const [parentObjectId, setParentObjectId] = useState<number | undefined>()
  const [reason, setReason] = useState("")
  const { data: configuration } = useAigcProjectConfiguration(project.id)
  const appendParent = action?.kind === "append" ? action.parent : undefined
  const addableTemplates = (configuration?.snapshot.slotTemplates ?? []).filter(
    (template) =>
      template.userAddable &&
      (!appendParent || template.parentTemplateKey === appendParent.blueprintTemplateKey)
  )
  const selectedTemplate = addableTemplates.find((template) => template.templateKey === templateKey)
  const requiredParentTemplateKey = selectedTemplate?.parentTemplateKey ?? null
  const parentCandidates = appendParent
    ? [appendParent]
    : requiredParentTemplateKey
      ? objects.filter((object) => object.blueprintTemplateKey === requiredParentTemplateKey)
      : []
  const appendObject = useAppendAigcProjectObject()
  const updateContract = useUpdateAigcProjectObjectContract()
  const removeObject = useRemoveAigcProjectObject()
  const pending = appendObject.isPending || updateContract.isPending || removeObject.isPending

  useEffect(() => {
    if (!action) return
    setDisplayName("")
    setTemplateKey(undefined)
    setContractRole(
      action.kind === "contract" ? (action.object.contractRole ?? "OPTIONAL") : "OPTIONAL"
    )
    setParentObjectId(action.kind === "append" ? action.parent?.id : undefined)
    setReason("")
  }, [action])

  function close() {
    if (!pending) onOpenChange(false)
  }

  function submit() {
    if (!mutable || !action) return
    if (action.kind === "append") {
      if (!displayName.trim() || !selectedTemplate) return
      appendObject.mutate(
        {
          projectId: project.id,
          parentObjectId,
          blueprintTemplateKey: selectedTemplate.templateKey,
          objectType: selectedTemplate.objectType,
          displayName: displayName.trim(),
          contractRole,
          semanticRole: selectedTemplate.semanticRole,
          outputContract: selectedTemplate.outputContract,
          expectedProjectVersion: project.version,
          expectedGraphRevision: project.graphRevision
        },
        {
          onSuccess: () => {
            notify.success("项目对象已追加")
            onOpenChange(false)
          }
        }
      )
      return
    }
    if (action.kind === "contract") {
      updateContract.mutate(
        {
          projectId: project.id,
          objectId: action.object.id,
          contractRole,
          expectedProjectVersion: project.version,
          expectedGraphRevision: project.graphRevision
        },
        {
          onSuccess: () => {
            notify.success("对象合同角色已更新")
            onOpenChange(false)
          }
        }
      )
      return
    }
    if (!reason.trim()) return
    removeObject.mutate(
      {
        projectId: project.id,
        objectId: action.object.id,
        expectedProjectVersion: project.version,
        expectedGraphRevision: project.graphRevision,
        reason: reason.trim()
      },
      {
        onSuccess: () => {
          notify.success("项目对象已移除")
          onOpenChange(false)
        }
      }
    )
  }

  const title =
    action?.kind === "append"
      ? action.parent
        ? "添加下级"
        : "添加内容"
      : action?.kind === "contract"
        ? "更新合同角色"
        : "移除项目对象"

  return (
    <Dialog
      open={action !== null && mutable}
      onOpenChange={(open) => {
        if (!open) close()
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {action?.kind === "append"
              ? action.parent
                ? `在「${action.parent.title || action.parent.stableKey}」下添加匹配的下级内容，父对象已锁定。`
                : "从当前项目结构的可添加类型中添加内容，并更新项目图谱 revision。"
              : action?.kind === "contract"
                ? `调整「${action.object.title || action.object.stableKey}」是否参与交付。`
                : `移除「${action?.object.title || action?.object.stableKey}」；存在子对象或活跃执行时服务端会拒绝。`}
          </DialogDescription>
        </DialogHeader>

        {action?.kind === "append" ? (
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor={displayNameId}>对象名称</Label>
              <Input
                id={displayNameId}
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                maxLength={200}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-2">
                <Label htmlFor={objectTypeId}>对象类型</Label>
                <Select
                  value={templateKey}
                  onValueChange={(value) => {
                    if (value) {
                      setTemplateKey(value)
                      setParentObjectId(appendParent?.id)
                    }
                  }}
                >
                  <SelectTrigger id={objectTypeId} className="w-full">
                    <SelectValue placeholder="从当前项目结构选择可添加类型" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {addableTemplates.map((template: AigcProjectSlotTemplateSpec) => (
                        <SelectItem key={template.templateKey} value={template.templateKey}>
                          {getObjectTypeConfig(template.objectType).label}
                          {template.displayNamePattern ? ` · ${template.displayNamePattern}` : ""}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                {addableTemplates.length === 0 ? (
                  <p className="text-muted-foreground text-xs">
                    当前项目结构未开放可添加对象类型。
                  </p>
                ) : null}
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor={parentSelectId}>父对象</Label>
                <Select
                  value={parentObjectId ? String(parentObjectId) : undefined}
                  disabled={Boolean(appendParent) || !requiredParentTemplateKey}
                  onValueChange={(value) => setParentObjectId(value ? Number(value) : undefined)}
                >
                  <SelectTrigger id={parentSelectId} className="w-full">
                    <SelectValue
                      placeholder={
                        !templateKey
                          ? appendParent
                            ? appendParent.title || appendParent.stableKey
                            : "先选择对象类型"
                          : requiredParentTemplateKey
                            ? "选择父对象"
                            : "该类型无需父对象"
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {parentCandidates.map((object) => (
                        <SelectItem key={object.id} value={String(object.id)}>
                          {object.title || object.stableKey}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                {appendParent ? (
                  <p className="text-muted-foreground text-xs">
                    父对象已锁定为「{appendParent.title || appendParent.stableKey}
                    」，仅显示匹配的下级类型。
                  </p>
                ) : requiredParentTemplateKey && parentCandidates.length === 0 ? (
                  <p className="text-muted-foreground text-xs">
                    当前项目缺少匹配的父对象，无法添加。
                  </p>
                ) : null}
              </div>
            </div>
          </div>
        ) : null}

        {action?.kind === "append" || action?.kind === "contract" ? (
          <div className="flex flex-col gap-2">
            <Label htmlFor={contractRoleId}>合同角色</Label>
            <Select
              value={contractRole}
              onValueChange={(value) => {
                if (value) setContractRole(value as AigcContractRole)
              }}
            >
              <SelectTrigger id={contractRoleId} className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {CONTRACT_ROLES.map((role) => (
                    <SelectItem key={role} value={role}>
                      {role}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        ) : null}

        {action?.kind === "remove" ? (
          <div className="flex flex-col gap-2">
            <Label htmlFor={reasonId}>移除原因</Label>
            <Textarea
              id={reasonId}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="填写移除原因（必填）"
            />
          </div>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" disabled={pending} onClick={close}>
            取消
          </Button>
          <Button
            type="button"
            variant={action?.kind === "remove" ? "destructive" : "default"}
            disabled={
              !mutable ||
              pending ||
              (action?.kind === "append" &&
                (!displayName.trim() ||
                  !selectedTemplate ||
                  (requiredParentTemplateKey !== null && !parentObjectId))) ||
              (action?.kind === "remove" && !reason.trim())
            }
            onClick={submit}
          >
            确认
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
