/**
 * AIGC Project 生命周期动作；按钮只在后端允许的阶段出现。
 * @author AaronZZH & Kiro
 */

"use client"

import { Archive, CheckCheck, CircleCheckBig, Send } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import type { AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectLifecycle,
  useAigcProjectSummary,
  useApproveAigcProjectReview
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

interface ProjectLifecycleActionsProps {
  project: AigcProject
  objects: AigcProjectObject[]
}

type PendingAction = "submit-review" | "approve-review" | "complete" | "archive"

const ACTION_COPY: Record<PendingAction, { title: string; description: string; confirm: string }> =
  {
    "submit-review": {
      title: "提交项目审核",
      description: "至少需要一个已采用对象版本。提交后项目进入只读审核阶段。",
      confirm: "提交审核"
    },
    "approve-review": {
      title: "通过项目审核",
      description: "审核对象将标记完成，项目进入交付阶段并允许收录 Work。",
      confirm: "通过审核"
    },
    complete: {
      title: "完成项目",
      description: "项目必须已收录至少一个未归档 Work，且已有 Publication 均已发布成功。",
      confirm: "完成项目"
    },
    archive: {
      title: "归档项目",
      description: "归档后图谱、版本、执行和作品引用保留，但项目整体只读。",
      confirm: "确认归档"
    }
  }

export function ProjectLifecycleActions({ project, objects }: ProjectLifecycleActionsProps) {
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const lifecycle = useAigcProjectLifecycle()
  const approveReview = useApproveAigcProjectReview()
  const { data: summary } = useAigcProjectSummary(project.id)
  const reviewObject = objects.find((object) => object.objectType === "review")
  const hasAdoptedVersion = objects.some((object) => object.adoptedVersionId !== undefined)
  const hasActiveWork = (summary?.activeWorkCount ?? 0) > 0
  const publicationsCompleted = (summary?.unpublishedPublicationCount ?? 0) === 0
  const completionReady = summary?.completionReady === true
  const isPending = lifecycle.isPending || approveReview.isPending

  function execute() {
    if (!pendingAction) return
    if (pendingAction === "approve-review") {
      if (!reviewObject) {
        notify.error("项目蓝图缺少审核对象，无法通过审核")
        return
      }
      approveReview.mutate(
        {
          projectId: project.id,
          reviewObjectId: reviewObject.id,
          expectedProjectVersion: project.version,
          conclusion: "审核通过"
        },
        {
          onSuccess: () => {
            setPendingAction(null)
            notify.success("项目审核已通过，进入交付阶段")
          }
        }
      )
      return
    }
    lifecycle.mutate(
      {
        projectId: project.id,
        action: pendingAction,
        expectedVersion: project.version
      },
      {
        onSuccess: () => {
          setPendingAction(null)
          notify.success(
            pendingAction === "submit-review"
              ? "项目已提交审核"
              : pendingAction === "complete"
                ? "项目已完成"
                : "项目已归档"
          )
        }
      }
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {project.status === "draft" || project.status === "in_progress" ? (
        <Button
          type="button"
          size="sm"
          title={hasAdoptedVersion ? undefined : "至少采用一个对象版本后才能提交审核"}
          disabled={isPending || !hasAdoptedVersion}
          onClick={() => setPendingAction("submit-review")}
        >
          <Send /> 提交审核
        </Button>
      ) : null}
      {project.status === "reviewing" ? (
        <Button
          type="button"
          size="sm"
          disabled={isPending || !reviewObject}
          onClick={() => setPendingAction("approve-review")}
        >
          <CheckCheck /> 通过审核
        </Button>
      ) : null}
      {project.status === "delivering" ? (
        <Button
          type="button"
          size="sm"
          title={
            !hasActiveWork
              ? "先从 adopted Deliverable 收录至少一个 Work"
              : !publicationsCompleted
                ? "仍有 Publication 未发布成功"
                : undefined
          }
          disabled={isPending || !completionReady}
          onClick={() => setPendingAction("complete")}
        >
          <CircleCheckBig /> 完成项目
        </Button>
      ) : null}
      {project.status !== "archived" ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={isPending}
          onClick={() => setPendingAction("archive")}
        >
          <Archive /> 归档
        </Button>
      ) : null}

      <ConfirmDialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) setPendingAction(null)
        }}
        title={pendingAction ? ACTION_COPY[pendingAction].title : "项目操作"}
        description={pendingAction ? ACTION_COPY[pendingAction].description : ""}
        confirmText={pendingAction ? ACTION_COPY[pendingAction].confirm : "确认"}
        onConfirm={execute}
      />
    </div>
  )
}
