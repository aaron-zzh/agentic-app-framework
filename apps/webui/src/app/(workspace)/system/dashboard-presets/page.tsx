"use client"

import { LayoutTemplate, Pencil, Plus, Trash2 } from "lucide-react"
import { useState } from "react"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import type { DashboardPresetVO } from "@/lib/api/rest/dashboard/dashboard"
import { notify } from "@/lib/notification"
import {
  useCreatePreset,
  useDeletePreset,
  usePresets,
  useUpdatePreset
} from "@/lib/queries/use-dashboard"

// ===== 类型 =====

interface FormState {
  name: string
  description: string
  adminOnly: boolean
  refreshInterval: string
}

const EMPTY_FORM: FormState = {
  name: "",
  description: "",
  adminOnly: false,
  refreshInterval: "300"
}

// ===== 主页面 =====

export default function DashboardPresetsPage() {
  const { data: presets, isLoading } = usePresets()

  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<DashboardPresetVO | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DashboardPresetVO | null>(null)

  return (
    <PageContainer>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="font-semibold text-xl">预设管理</h1>
            <p className="text-muted-foreground text-sm">
              管理仪表盘预设模板，用户可从预设快速创建仪表盘。
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            新建预设
          </Button>
        </div>

        {/* 列表 */}
        {isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={`skeleton-${i}`} className="h-16 w-full" />
            ))}
          </div>
        ) : (
          <PresetTable presets={presets ?? []} onEdit={setEditTarget} onDelete={setDeleteTarget} />
        )}
      </div>

      {/* 新建弹窗 */}
      <PresetFormDialog open={createOpen} onClose={() => setCreateOpen(false)} />

      {/* 编辑弹窗 */}
      {editTarget && (
        <PresetFormDialog open preset={editTarget} onClose={() => setEditTarget(null)} />
      )}

      {/* 删除确认 */}
      {deleteTarget && (
        <DeleteConfirmDialog preset={deleteTarget} onClose={() => setDeleteTarget(null)} />
      )}
    </PageContainer>
  )
}

// ===== 列表 =====

function PresetTable({
  presets,
  onEdit,
  onDelete
}: {
  presets: DashboardPresetVO[]
  onEdit: (p: DashboardPresetVO) => void
  onDelete: (p: DashboardPresetVO) => void
}) {
  if (presets.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed py-16 text-muted-foreground">
        <LayoutTemplate className="size-10 opacity-30" />
        <p className="text-sm">暂无预设，点击「新建预设」创建</p>
      </div>
    )
  }

  return (
    <div className="rounded-lg border bg-card">
      {/* 表头 */}
      <div className="grid grid-cols-[1fr_200px_80px_100px_100px] gap-4 border-b bg-muted/40 px-4 py-3 font-medium text-muted-foreground text-sm">
        <span>名称</span>
        <span>描述</span>
        <span>Widget 数</span>
        <span>仅管理员</span>
        <span className="text-right">操作</span>
      </div>

      {presets.map((preset) => (
        <div
          key={preset.id}
          className="grid grid-cols-[1fr_200px_80px_100px_100px] items-center gap-4 border-b px-4 py-3 text-sm last:border-b-0"
        >
          <div>
            <p className="font-medium">{preset.name}</p>
            <p className="text-muted-foreground text-xs">{preset.presetKey}</p>
          </div>
          <p className="line-clamp-2 text-muted-foreground text-xs">{preset.description || "—"}</p>
          <span>{Array.isArray(preset.widgets) ? preset.widgets.length : 0}</span>
          <span>
            {preset.adminOnly ? (
              <Badge variant="secondary">仅管理员</Badge>
            ) : (
              <span className="text-muted-foreground">公开</span>
            )}
          </span>
          <div className="flex items-center justify-end gap-1">
            <Button variant="ghost" size="icon" onClick={() => onEdit(preset)}>
              <Pencil className="size-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="text-destructive hover:text-destructive"
              onClick={() => onDelete(preset)}
            >
              <Trash2 className="size-4" />
            </Button>
          </div>
        </div>
      ))}
    </div>
  )
}

// ===== 新建/编辑弹窗 =====

function PresetFormDialog({
  open,
  preset,
  onClose
}: {
  open: boolean
  preset?: DashboardPresetVO
  onClose: () => void
}) {
  const isEdit = !!preset
  const [form, setForm] = useState<FormState>(
    preset
      ? {
          name: preset.name,
          description: preset.description ?? "",
          adminOnly: preset.adminOnly,
          refreshInterval: String(preset.refreshInterval ?? 300)
        }
      : EMPTY_FORM
  )

  const createMutation = useCreatePreset()
  const updateMutation = useUpdatePreset()
  const isPending = createMutation.isPending || updateMutation.isPending

  function patch(key: keyof FormState, value: string | boolean) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit() {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      adminOnly: form.adminOnly,
      refreshInterval: Number(form.refreshInterval) || 300
    }

    if (!payload.name) {
      notify.error("请填写预设名称")
      return
    }

    try {
      if (isEdit && preset) {
        await updateMutation.mutateAsync({ id: preset.id, data: payload })
        notify.success("预设已更新")
      } else {
        await createMutation.mutateAsync({ ...payload, widgets: [] })
        notify.success("预设已创建")
      }
      onClose()
    } catch {
      // mutation 内部已有全局错误处理
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? "编辑预设" : "新建预设"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <Label htmlFor="preset-name">名称 *</Label>
            <Input
              id="preset-name"
              value={form.name}
              onChange={(e) => patch("name", e.target.value)}
              placeholder="预设名称"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="preset-desc">描述</Label>
            <Textarea
              id="preset-desc"
              value={form.description}
              onChange={(e) => patch("description", e.target.value)}
              placeholder="简短描述此预设的用途"
              rows={2}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="preset-refresh">刷新间隔（秒）</Label>
            <Input
              id="preset-refresh"
              type="number"
              min={30}
              value={form.refreshInterval}
              onChange={(e) => patch("refreshInterval", e.target.value)}
            />
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="preset-admin-only"
              checked={form.adminOnly}
              onCheckedChange={(v) => patch("adminOnly", v === true)}
            />
            <Label htmlFor="preset-admin-only" className="cursor-pointer">
              仅管理员可见
            </Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={isPending}>
            {isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ===== 删除确认弹窗 =====

function DeleteConfirmDialog({
  preset,
  onClose
}: {
  preset: DashboardPresetVO
  onClose: () => void
}) {
  const deleteMutation = useDeletePreset()

  async function handleDelete() {
    try {
      await deleteMutation.mutateAsync(preset.id)
      notify.success("预设已删除")
      onClose()
    } catch {
      // 全局错误处理
    }
  }

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>确认删除</DialogTitle>
        </DialogHeader>
        <p className="text-muted-foreground text-sm">
          确定要删除预设「<span className="font-medium text-foreground">{preset.name}</span>
          」吗？此操作不可恢复。
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={deleteMutation.isPending}>
            取消
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}>
            {deleteMutation.isPending ? "删除中…" : "确认删除"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
